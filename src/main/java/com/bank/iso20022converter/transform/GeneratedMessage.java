package com.bank.iso20022converter.transform;

/** One output message of a transformation, tagged with its message type. */
public record GeneratedMessage(String messageId, Object payload) {
}
