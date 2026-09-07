package com.bank.iso20022converter.core;

/**
 * Metadata for one curated, typed ISO 20022 message. Every message in this project's
 * curated set uses "Document" as its root element local name (matches the real ISO
 * 20022 convention), so only the namespace varies per type.
 *
 * @param messageId    dotted identifier, e.g. "pain.001.001.09"
 * @param namespaceUri the message's XML namespace, e.g. "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09"
 * @param documentClass the XJC-generated root "Document" class for this message
 * @param displayName  human-readable name for the catalog endpoint
 */
public record MessageTypeDescriptor(
        String messageId,
        String namespaceUri,
        Class<?> documentClass,
        String displayName) {

    public static final String ROOT_LOCAL_NAME = "Document";
}
