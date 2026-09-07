package com.bank.iso20022converter.core.exception;

/** Thrown when the input XML/JSON cannot be parsed, or fails schema-level validation. */
public class MalformedMessageException extends RuntimeException {

    public MalformedMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    public MalformedMessageException(String message) {
        super(message);
    }
}
