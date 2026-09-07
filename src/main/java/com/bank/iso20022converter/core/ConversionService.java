package com.bank.iso20022converter.core;

/** Plain, non-enrichment XML&lt;-&gt;JSON conversion (no cross-message-type mapping). */
public interface ConversionService {

    /**
     * @param messageIdHint optional; if omitted, the message type is auto-detected
     *                      from the XML root element's namespace
     */
    XmlToJsonResult xmlToJson(String xml, String messageIdHint);

    /**
     * @param messageId required - JSON carries no namespace to auto-detect from. If
     *                  it matches a curated type, typed conversion is used; otherwise
     *                  it's used as the generic fallback's XML root element name.
     */
    String jsonToXml(String json, String messageId);
}
