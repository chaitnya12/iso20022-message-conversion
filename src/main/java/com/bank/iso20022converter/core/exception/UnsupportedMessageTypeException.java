package com.bank.iso20022converter.core.exception;

/** Thrown when a caller-supplied or auto-detected message identifier/namespace is unknown. */
public class UnsupportedMessageTypeException extends RuntimeException {

    public UnsupportedMessageTypeException(String message) {
        super(message);
    }
}
