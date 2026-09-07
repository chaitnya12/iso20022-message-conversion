package com.bank.iso20022converter.web;

import com.bank.iso20022converter.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClearingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serialConversionReturnsSinglePacs008() throws Exception {
        Map<String, Object> request = Map.of(
                "sourceXml", TestFixtures.pain001Sample(),
                "settlementMethod", "SERIAL",
                "enrichment", Map.of("complianceStatus", "CLEARED"));

        mockMvc.perform(post("/api/v1/clearing/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.messages[0].messageId").value("pacs.008.001.08"))
                .andExpect(jsonPath("$.messages[0].xml", org.hamcrest.Matchers.containsString("FIToFICstmrCdtTrf")));
    }

    @Test
    void coverConversionReturnsCorrelatedPair() throws Exception {
        Map<String, Object> request = Map.of(
                "sourceXml", TestFixtures.pain001Sample(),
                "settlementMethod", "COVER",
                "enrichment", Map.of(
                        "complianceStatus", "CLEARED",
                        "intermediaryAgents", List.of(Map.of("bic", "CHASUS33", "role", "INTERMEDIARY1"))));

        mockMvc.perform(post("/api/v1/clearing/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.messages[0].messageId").value("pacs.008.001.08"))
                .andExpect(jsonPath("$.messages[1].messageId").value("pacs.009.001.08"));
    }

    @Test
    void missingComplianceStatusReturns422WithMissingEnrichmentErrorCode() throws Exception {
        Map<String, Object> request = Map.of(
                "sourceXml", TestFixtures.pain001Sample(),
                "settlementMethod", "SERIAL");

        mockMvc.perform(post("/api/v1/clearing/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("MISSING_ENRICHMENT"));
    }

    @Test
    void unknownSourceNamespaceReturns400() throws Exception {
        String bogusXml = "<Document xmlns=\"urn:example:not-iso20022\"><Foo/></Document>";
        Map<String, Object> request = Map.of("sourceXml", bogusXml);

        mockMvc.perform(post("/api/v1/clearing/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MESSAGE_TYPE"));
    }

    @Test
    void returnStatusMapsRejectionToPain002() throws Exception {
        Map<String, Object> request = Map.of("sourceXml", TestFixtures.pacs002RejectionSample());

        mockMvc.perform(post("/api/v1/clearing/return-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].messageId").value("pain.002.001.10"))
                .andExpect(jsonPath("$.messages[0].xml", org.hamcrest.Matchers.containsString("RJCT")));
    }
}
