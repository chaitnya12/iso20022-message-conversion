package com.bank.iso20022converter.web;

import com.bank.iso20022converter.core.ConversionService;
import com.bank.iso20022converter.core.XmlToJsonResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Plain, non-enrichment XML&lt;-&gt;JSON conversion. Raw payload bodies in/out; metadata rides in response headers. */
@RestController
@RequestMapping("/api/v1/convert")
public class ConversionController {

    private static final String MESSAGE_ID_HEADER = "X-Iso20022-Message-Id";
    private static final String CONVERSION_MODE_HEADER = "X-Conversion-Mode";

    private final ConversionService conversionService;

    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @PostMapping(value = "/xml-to-json", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> xmlToJson(
            @RequestBody String xml,
            @RequestParam(required = false) String messageId) {
        XmlToJsonResult result = conversionService.xmlToJson(xml, messageId);
        return ResponseEntity.ok()
                .header(MESSAGE_ID_HEADER, result.messageId())
                .header(CONVERSION_MODE_HEADER, result.typed() ? "typed" : "generic")
                .body(result.json());
    }

    @PostMapping(value = "/json-to-xml", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> jsonToXml(
            @RequestBody String json,
            @RequestParam String messageId) {
        String xml = conversionService.jsonToXml(json, messageId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                .body(xml);
    }
}
