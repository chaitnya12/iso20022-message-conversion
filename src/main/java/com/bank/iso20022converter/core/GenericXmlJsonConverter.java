package com.bank.iso20022converter.core;

import com.bank.iso20022converter.core.exception.MalformedMessageException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Structural, schema-agnostic XML&lt;-&gt;JSON fallback for any message type that
 * doesn't have generated JAXB classes registered in {@link MessageTypeRegistry}. No
 * schema validation is performed; this is a best-effort tree conversion.
 */
@Component
public class GenericXmlJsonConverter {

    private final XmlMapper xmlMapper;
    private final ObjectMapper jsonMapper;

    public GenericXmlJsonConverter(XmlMapper xmlMapper, @Qualifier("genericJsonMapper") ObjectMapper jsonMapper) {
        this.xmlMapper = xmlMapper;
        this.jsonMapper = jsonMapper;
    }

    public String xmlToJson(String xml) {
        try {
            JsonNode tree = xmlMapper.readTree(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
        } catch (IOException e) {
            throw new MalformedMessageException("Failed to parse XML generically: " + e.getMessage(), e);
        }
    }

    /**
     * @param rootName wrapper element name for the regenerated XML - for the generic
     *                 fallback path, this is the caller-supplied {@code messageId},
     *                 since there's no schema to derive a root element from.
     */
    public String jsonToXml(String json, String rootName) {
        try {
            JsonNode tree = jsonMapper.readTree(json);
            return xmlMapper.writer().withRootName(rootName).writeValueAsString(tree);
        } catch (IOException e) {
            throw new MalformedMessageException("Failed to parse JSON generically: " + e.getMessage(), e);
        }
    }
}
