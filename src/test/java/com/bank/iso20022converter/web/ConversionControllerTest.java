package com.bank.iso20022converter.web;

import com.bank.iso20022converter.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConversionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void typedXmlToJsonRoundTripsBackToXml() throws Exception {
        MvcResult jsonResult = mockMvc.perform(post("/api/v1/convert/xml-to-json")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(TestFixtures.pain001Sample()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Iso20022-Message-Id", "pain.001.001.09"))
                .andExpect(header().string("X-Conversion-Mode", "typed"))
                .andExpect(content().string(containsString("Acme Corp")))
                .andReturn();

        String json = jsonResult.getResponse().getContentAsString();

        mockMvc.perform(post("/api/v1/convert/json-to-xml")
                        .param("messageId", "pain.001.001.09")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Acme Corp")))
                .andExpect(content().string(containsString("E2E-0001")));
    }

    @Test
    void unknownMessageTypeFallsBackToGenericConversion() throws Exception {
        String genericXml = "<Root xmlns=\"urn:example:not-iso20022\"><Field>value</Field></Root>";

        mockMvc.perform(post("/api/v1/convert/xml-to-json")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(genericXml))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Conversion-Mode", "generic"))
                .andExpect(content().string(containsString("value")));
    }

    @Test
    void malformedXmlReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/convert/xml-to-json")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("not xml at all"))
                .andExpect(status().isBadRequest());
    }
}
