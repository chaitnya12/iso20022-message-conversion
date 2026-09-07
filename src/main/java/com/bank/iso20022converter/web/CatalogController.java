package com.bank.iso20022converter.web;

import com.bank.iso20022converter.core.MessageTypeRegistry;
import com.bank.iso20022converter.transform.TransformerRegistry;
import com.bank.iso20022converter.web.dto.MessageTypeDescriptorDto;
import com.bank.iso20022converter.web.dto.TransformerPairDescriptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Discoverability: which message types are typed/curated, and which transformer pairs exist. */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final MessageTypeRegistry typeRegistry;
    private final TransformerRegistry transformerRegistry;

    public CatalogController(MessageTypeRegistry typeRegistry, TransformerRegistry transformerRegistry) {
        this.typeRegistry = typeRegistry;
        this.transformerRegistry = transformerRegistry;
    }

    @GetMapping("/message-types")
    public List<MessageTypeDescriptorDto> messageTypes() {
        return typeRegistry.all().stream()
                .map(d -> new MessageTypeDescriptorDto(d.messageId(), d.namespaceUri(), d.displayName(), true))
                .toList();
    }

    @GetMapping("/transformers")
    public List<TransformerPairDescriptor> transformers() {
        return transformerRegistry.availablePairs();
    }
}
