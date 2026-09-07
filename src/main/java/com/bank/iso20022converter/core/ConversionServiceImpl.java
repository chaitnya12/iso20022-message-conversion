package com.bank.iso20022converter.core;

import com.bank.iso20022converter.core.exception.MalformedMessageException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ConversionServiceImpl implements ConversionService {

    private final MessageTypeRegistry registry;
    private final JaxbMessageCodec jaxbCodec;
    private final GenericXmlJsonConverter genericConverter;
    private final ObjectMapper typedJsonMapper;

    public ConversionServiceImpl(
            MessageTypeRegistry registry,
            JaxbMessageCodec jaxbCodec,
            GenericXmlJsonConverter genericConverter,
            @Qualifier("typedJsonMapper") ObjectMapper typedJsonMapper) {
        this.registry = registry;
        this.jaxbCodec = jaxbCodec;
        this.genericConverter = genericConverter;
        this.typedJsonMapper = typedJsonMapper;
    }

    @Override
    public XmlToJsonResult xmlToJson(String xml, String messageIdHint) {
        String namespace = XmlNamespaceSniffer.rootNamespace(xml);
        Optional<MessageTypeDescriptor> descriptor = resolveForXml(namespace, messageIdHint);

        if (descriptor.isPresent()) {
            MessageTypeDescriptor d = descriptor.get();
            Object document = jaxbCodec.unmarshal(xml, d);
            try {
                String json = typedJsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
                return new XmlToJsonResult(json, d.messageId(), true);
            } catch (JsonProcessingException e) {
                throw new MalformedMessageException("Failed to serialize " + d.messageId() + " to JSON: " + e.getMessage(), e);
            }
        }

        String json = genericConverter.xmlToJson(xml);
        return new XmlToJsonResult(json, namespace, false);
    }

    @Override
    public String jsonToXml(String json, String messageId) {
        Optional<MessageTypeDescriptor> descriptor = registry.findByMessageId(messageId);

        if (descriptor.isPresent()) {
            MessageTypeDescriptor d = descriptor.get();
            Object document;
            try {
                document = typedJsonMapper.readValue(json, d.documentClass());
            } catch (JsonProcessingException e) {
                throw new MalformedMessageException("Failed to parse JSON as " + d.messageId() + ": " + e.getMessage(), e);
            }
            return jaxbCodec.marshal(document, d);
        }

        return genericConverter.jsonToXml(json, messageId);
    }

    /** Resolves a descriptor by hint first (if it names a curated type), else by the sniffed XML namespace. */
    private Optional<MessageTypeDescriptor> resolveForXml(String namespace, String messageIdHint) {
        if (messageIdHint != null && !messageIdHint.isBlank()) {
            Optional<MessageTypeDescriptor> byHint = registry.findByMessageId(messageIdHint);
            if (byHint.isPresent()) {
                return byHint;
            }
        }
        return registry.findByNamespace(namespace);
    }
}
