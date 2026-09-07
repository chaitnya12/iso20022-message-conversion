package com.bank.iso20022converter.core.exception;

/** Thrown when source/target message types are both valid but no transformer pair is registered for them. */
public class NoTransformerFoundException extends RuntimeException {

    public NoTransformerFoundException(String message) {
        super(message);
    }
}
