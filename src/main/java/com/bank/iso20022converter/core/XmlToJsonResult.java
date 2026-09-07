package com.bank.iso20022converter.core;

/**
 * @param json      the converted JSON payload
 * @param messageId resolved message identifier (registry messageId if typed, sniffed
 *                  namespace URI if the generic fallback was used and no hint matched)
 * @param typed     true if the typed JAXB-backed path was used, false if the generic
 *                  structural fallback was used
 */
public record XmlToJsonResult(String json, String messageId, boolean typed) {
}
