package com.bank.iso20022converter.transform;

/** One documented field-mapping decision, returned to the caller for audit/transparency. */
public record MappingNote(String field, Action action, String description) {

    public enum Action {
        /** Copied through from the source message unchanged. */
        COPIED,
        /** Not present in the source; the tool generated a safe, harmless value (e.g. a new UETR). */
        GENERATED,
        /** Not present in the source or enrichment; a documented business default was applied. */
        DEFAULTED,
        /** Mapped from a source field whose semantics don't exactly match the target field. */
        APPROXIMATED,
        /** Supplied by the caller's enrichment payload. */
        FROM_ENRICHMENT
    }
}
