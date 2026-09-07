package com.bank.iso20022converter.web.dto;

/** One entry in the GET /api/v1/transformers catalog. */
public record TransformerPairDescriptor(String sourceMessageId, String targetMessageId) {
}
