package com.bank.iso20022converter.transform;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Supplies safe defaults ONLY for fields where a wrong guess is harmless - never for
 * compliance status, FX rate, or correspondent routing, where a wrong default could
 * misroute real money (those are hard failures in the transformers instead, see
 * {@code MappingException.MISSING_ENRICHMENT}). This asymmetry is deliberate. Swap
 * this component out to change default behavior without touching transformer logic.
 */
@Component
public class EnrichmentDefaults {

    /** Reuses a UETR the source message already carries; generates a fresh one otherwise. */
    public String resolveOrGenerateUetr(String sourceUetr) {
        return (sourceUetr != null && !sourceUetr.isBlank()) ? sourceUetr : UUID.randomUUID().toString();
    }

    /** Default SttlmMtd code per settlement method, when neither the source nor enrichment specifies one. */
    public String defaultSettlementMethodCode(SettlementMethod method) {
        return switch (method) {
            case COVER -> "COVE";
            case SERIAL, NOT_APPLICABLE -> "CLRG";
        };
    }
}
