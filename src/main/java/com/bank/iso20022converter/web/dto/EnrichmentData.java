package com.bank.iso20022converter.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Caller-supplied results of the payment engine's own business processing (posting,
 * earmarking, compliance/flex checks, FX, correspondent routing) that this conversion
 * tool needs but never computes itself. See the plan's asymmetric default policy:
 * {@code complianceStatus} and {@code exchangeRate} are never guessed - a missing
 * required field fails the request rather than silently defaulting, because a wrong
 * guess here would misroute real money.
 */
public record EnrichmentData(
        /** Must be exactly "CLEARED" for a transformation to proceed. */
        String complianceStatus,
        /** If the settlement currency differs from the source instructed currency, required; never defaulted. */
        BigDecimal exchangeRate,
        /** Target settlement currency; omit or match the source currency for a same-currency payment. */
        String settlementCurrency,
        /** Optional; copied into an audit/remittance field if present. */
        String earmarkReference,
        /** Required for settlementMethod=COVER unless the transformer can resolve routing another way. */
        List<IntermediaryAgent> intermediaryAgents,
        /** Optional; falls back to the source message's own charge-bearer code if present. */
        String chargeBearer) {

    public record IntermediaryAgent(String bic, String role) {
    }

    public static EnrichmentData empty() {
        return new EnrichmentData(null, null, null, null, List.of(), null);
    }

    public List<IntermediaryAgent> intermediaryAgentsOrEmpty() {
        return intermediaryAgents == null ? List.of() : intermediaryAgents;
    }
}
