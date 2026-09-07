package com.bank.iso20022converter.core;

import com.bank.iso20022converter.core.exception.UnsupportedMessageTypeException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Curated registry of the ISO 20022 message types this project has generated typed
 * JAXB classes for. Deliberately a small, explicit static map rather than classpath
 * scanning - the curated set is small, and being explicit here is easier to reason
 * about and extend than discovering generated packages by convention.
 *
 * <p>Any message type NOT in this registry still works on the plain xml-to-json /
 * json-to-xml endpoints via {@link GenericXmlJsonConverter} (structural, no schema
 * validation) - it just isn't eligible for typed cross-message-type transformation.
 * Adding a new curated type = add its XSD under src/main/resources/xsd, then register
 * it here.
 */
@Component
public class MessageTypeRegistry {

    private final Map<String, MessageTypeDescriptor> byMessageId = new LinkedHashMap<>();
    private final Map<String, MessageTypeDescriptor> byNamespace = new LinkedHashMap<>();

    public MessageTypeRegistry() {
        register(new MessageTypeDescriptor(
                "pain.001.001.09",
                "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09",
                com.bank.iso20022converter.model.pain00100109.Document.class,
                "Customer Credit Transfer Initiation"));
        register(new MessageTypeDescriptor(
                "pain.002.001.10",
                "urn:iso:std:iso:20022:tech:xsd:pain.002.001.10",
                com.bank.iso20022converter.model.pain00200110.Document.class,
                "Customer Payment Status Report"));
        register(new MessageTypeDescriptor(
                "pacs.008.001.08",
                "urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08",
                com.bank.iso20022converter.model.pacs00800108.Document.class,
                "FI To FI Customer Credit Transfer"));
        register(new MessageTypeDescriptor(
                "pacs.009.001.08",
                "urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08",
                com.bank.iso20022converter.model.pacs00900108.Document.class,
                "Financial Institution Credit Transfer (cover)"));
        register(new MessageTypeDescriptor(
                "pacs.002.001.10",
                "urn:iso:std:iso:20022:tech:xsd:pacs.002.001.10",
                com.bank.iso20022converter.model.pacs00200110.Document.class,
                "FI To FI Payment Status Report"));
    }

    private void register(MessageTypeDescriptor descriptor) {
        byMessageId.put(descriptor.messageId(), descriptor);
        byNamespace.put(descriptor.namespaceUri(), descriptor);
    }

    public Optional<MessageTypeDescriptor> findByMessageId(String messageId) {
        return Optional.ofNullable(byMessageId.get(messageId));
    }

    public Optional<MessageTypeDescriptor> findByNamespace(String namespaceUri) {
        return Optional.ofNullable(byNamespace.get(namespaceUri));
    }

    public MessageTypeDescriptor requireByMessageId(String messageId) {
        return findByMessageId(messageId)
                .orElseThrow(() -> new UnsupportedMessageTypeException(
                        "Unknown or unregistered message type '" + messageId
                                + "'. See GET /api/v1/message-types for the curated set."));
    }

    public Collection<MessageTypeDescriptor> all() {
        return byMessageId.values();
    }
}
