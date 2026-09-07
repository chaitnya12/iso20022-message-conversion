package com.bank.iso20022converter.transform;

import com.bank.iso20022converter.core.JaxbMessageCodec;
import com.bank.iso20022converter.core.MessageTypeDescriptor;
import com.bank.iso20022converter.core.MessageTypeRegistry;
import com.bank.iso20022converter.core.XmlNamespaceSniffer;
import com.bank.iso20022converter.core.exception.NoTransformerFoundException;
import com.bank.iso20022converter.core.exception.UnsupportedMessageTypeException;
import com.bank.iso20022converter.validation.CbprPlusValidator;
import com.bank.iso20022converter.web.dto.ClearingConversionRequest;
import com.bank.iso20022converter.web.dto.ClearingConversionResponse;
import com.bank.iso20022converter.web.dto.GeneratedMessageDto;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a cross-message-type ("clearing") conversion: resolve the source type,
 * find its registered transformer, run it with the caller's enrichment, optionally
 * CBPR+-validate every output, then marshal each output back to XML.
 */
@Service
public class ClearingConversionService {

    private final MessageTypeRegistry typeRegistry;
    private final TransformerRegistry transformerRegistry;
    private final JaxbMessageCodec jaxbCodec;
    private final CbprPlusValidator cbprPlusValidator;

    public ClearingConversionService(
            MessageTypeRegistry typeRegistry,
            TransformerRegistry transformerRegistry,
            JaxbMessageCodec jaxbCodec,
            CbprPlusValidator cbprPlusValidator) {
        this.typeRegistry = typeRegistry;
        this.transformerRegistry = transformerRegistry;
        this.jaxbCodec = jaxbCodec;
        this.cbprPlusValidator = cbprPlusValidator;
    }

    public ClearingConversionResponse convert(ClearingConversionRequest request) {
        String namespace = XmlNamespaceSniffer.rootNamespace(request.sourceXml());
        MessageTypeDescriptor sourceDescriptor = typeRegistry.findByNamespace(namespace)
                .orElseThrow(() -> new UnsupportedMessageTypeException(
                        "Unrecognized source message namespace '" + namespace
                                + "'. See GET /api/v1/message-types for the curated, typed set."));

        @SuppressWarnings("unchecked")
        MessageTransformer<Object> transformer = (MessageTransformer<Object>) transformerRegistry
                .find(sourceDescriptor.messageId())
                .orElseThrow(() -> new NoTransformerFoundException(
                        "No transformer registered for source type '" + sourceDescriptor.messageId()
                                + "'. See GET /api/v1/transformers for the available pairs."));

        if (request.targetMessageId() != null
                && !transformer.supportedTargetMessageIds().contains(request.targetMessageId())) {
            throw new NoTransformerFoundException(
                    "The transformer for '" + sourceDescriptor.messageId() + "' does not support target '"
                            + request.targetMessageId() + "'. Supported targets: "
                            + transformer.supportedTargetMessageIds());
        }

        Object source = jaxbCodec.unmarshal(request.sourceXml(), sourceDescriptor);

        // Transformers with no settlement-method concept (e.g. status-report mapping)
        // simply ignore this parameter; SERIAL is the sensible default for those that do use it.
        TransformationResult result = transformer.transform(
                source, request.enrichmentOrEmpty(), request.settlementMethodOrDefault());

        if (request.strictOrDefault()) {
            for (GeneratedMessage generated : result.messages()) {
                cbprPlusValidator.validate(generated);
            }
            if (result.messages().size() > 1) {
                cbprPlusValidator.validateCoverPairCorrelation(result.messages());
            }
        }

        var messages = result.messages().stream()
                .map(m -> new GeneratedMessageDto(
                        m.messageId(),
                        jaxbCodec.marshal(m.payload(), typeRegistry.requireByMessageId(m.messageId()))))
                .toList();

        return new ClearingConversionResponse(messages, result.notes());
    }
}
