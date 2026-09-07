package com.bank.iso20022converter.web.dto;

/** One entry in the GET /api/v1/message-types catalog. */
public record MessageTypeDescriptorDto(String messageId, String namespaceUri, String displayName, boolean typed) {
}
