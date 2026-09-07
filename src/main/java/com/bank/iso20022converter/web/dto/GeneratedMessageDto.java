package com.bank.iso20022converter.web.dto;

/** One output message of a clearing conversion, serialized back to XML. */
public record GeneratedMessageDto(String messageId, String xml) {
}
