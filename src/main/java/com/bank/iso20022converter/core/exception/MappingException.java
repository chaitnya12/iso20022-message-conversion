package com.bank.iso20022converter.core.exception;

import java.util.List;

/**
 * Thrown when a registered {@link com.bank.iso20022converter.transform.MessageTransformer}
 * cannot complete a transformation - most commonly because the caller omitted a required
 * {@code enrichment} field the target clearing message needs but the source message
 * doesn't carry (see the plan's asymmetric default policy: a wrong guess for money-moving
 * data is worse than a hard failure).
 */
public class MappingException extends RuntimeException {

    /** Machine-readable discriminator so callers can tell "you forgot a field" from other failures. */
    public static final String MISSING_ENRICHMENT = "MISSING_ENRICHMENT";
    public static final String MAPPING_FAILED = "MAPPING_FAILED";

    private final String errorCode;
    private final List<String> violations;

    public MappingException(String errorCode, String message, List<String> violations) {
        super(message);
        this.errorCode = errorCode;
        this.violations = violations;
    }

    public MappingException(String errorCode, String message) {
        this(errorCode, message, List.of(message));
    }

    public String getErrorCode() {
        return errorCode;
    }

    public List<String> getViolations() {
        return violations;
    }
}
