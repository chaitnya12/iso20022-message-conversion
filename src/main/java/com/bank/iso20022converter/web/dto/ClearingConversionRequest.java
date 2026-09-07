package com.bank.iso20022converter.web.dto;

import com.bank.iso20022converter.transform.SettlementMethod;
import jakarta.validation.constraints.NotBlank;

/**
 * Request envelope for POST /api/v1/clearing/convert and /api/v1/clearing/return-status.
 * A JSON envelope (rather than a raw XML body) because cross-message-type conversion
 * needs structured enrichment input and can produce more than one output message.
 *
 * @param sourceXml        the source message as raw XML text
 * @param targetMessageId  optional; if given, must be one the resolved transformer
 *                         actually supports for the requested settlementMethod - a
 *                         sanity check, since the transformer is really selected by
 *                         source type + settlementMethod
 * @param settlementMethod SERIAL or COVER; ignored (NOT_APPLICABLE) by transformers
 *                         with no settlement-method concept, e.g. the return-status path
 * @param strict           if true (default), generated clearing messages must pass
 *                         {@link com.bank.iso20022converter.validation.CbprPlusValidator}
 * @param enrichment        caller-supplied business data; omit for transformers that need none
 */
public record ClearingConversionRequest(
        @NotBlank String sourceXml,
        String targetMessageId,
        SettlementMethod settlementMethod,
        Boolean strict,
        EnrichmentData enrichment) {

    public SettlementMethod settlementMethodOrDefault() {
        return settlementMethod == null ? SettlementMethod.SERIAL : settlementMethod;
    }

    public boolean strictOrDefault() {
        return strict == null || strict;
    }

    public EnrichmentData enrichmentOrEmpty() {
        return enrichment == null ? EnrichmentData.empty() : enrichment;
    }
}
