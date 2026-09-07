package com.bank.iso20022converter.transform;

/** Correspondent banking model for a cross-border credit transfer. */
public enum SettlementMethod {
    /** Single pacs.008 travels the correspondent chain directly. */
    SERIAL,
    /** A correlated pacs.008 + pacs.009 pair, sharing one UETR, per SWIFT cover-payment convention. */
    COVER,
    /** For transformers with no settlement-method concept (e.g. status-report mapping). */
    NOT_APPLICABLE
}
