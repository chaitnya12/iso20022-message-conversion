package com.bank.iso20022converter.validation;

import java.util.List;

/**
 * Thrown when a generated clearing-bound message fails the CBPR+-aligned checks in
 * {@link CbprPlusValidator}. Kept as a distinct type (rather than reusing
 * {@code MappingException}) so callers can tell "the generated message itself is
 * non-conformant" apart from "you forgot to supply an enrichment field" via a
 * separate error code, per the plan's REST error contract.
 */
public class CbprPlusViolationException extends RuntimeException {

    public static final String ERROR_CODE = "CBPR_PLUS_VIOLATION";

    private final List<String> violations;

    public CbprPlusViolationException(List<String> violations) {
        super("CBPR+ validation failed: " + String.join("; ", violations));
        this.violations = violations;
    }

    public List<String> getViolations() {
        return violations;
    }
}
