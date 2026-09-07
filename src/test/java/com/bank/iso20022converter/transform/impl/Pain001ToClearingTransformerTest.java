package com.bank.iso20022converter.transform.impl;

import com.bank.iso20022converter.TestFixtures;
import com.bank.iso20022converter.core.JaxbMessageCodec;
import com.bank.iso20022converter.core.MessageTypeRegistry;
import com.bank.iso20022converter.core.exception.MappingException;
import com.bank.iso20022converter.model.pacs00800108.CreditTransferTransaction;
import com.bank.iso20022converter.model.pain00100109.Document;
import com.bank.iso20022converter.transform.EnrichmentDefaults;
import com.bank.iso20022converter.transform.GeneratedMessage;
import com.bank.iso20022converter.transform.SettlementMethod;
import com.bank.iso20022converter.transform.TransformationResult;
import com.bank.iso20022converter.web.dto.EnrichmentData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Pain001ToClearingTransformerTest {

    private final MessageTypeRegistry registry = new MessageTypeRegistry();
    private final JaxbMessageCodec codec = new JaxbMessageCodec();
    private final Pain001ToClearingTransformer transformer = new Pain001ToClearingTransformer(new EnrichmentDefaults());

    private Document loadSource() {
        return (Document) codec.unmarshal(TestFixtures.pain001Sample(), registry.requireByMessageId("pain.001.001.09"));
    }

    private static EnrichmentData cleared() {
        return new EnrichmentData("CLEARED", null, null, null, List.of(), null);
    }

    @Test
    void serialModeProducesSinglePacs008WithGeneratedUetr() {
        TransformationResult result = transformer.transform(loadSource(), cleared(), SettlementMethod.SERIAL);

        assertThat(result.messages()).hasSize(1);
        GeneratedMessage msg = result.messages().get(0);
        assertThat(msg.messageId()).isEqualTo("pacs.008.001.08");

        var doc = (com.bank.iso20022converter.model.pacs00800108.Document) msg.payload();
        CreditTransferTransaction txn = doc.getFIToFICstmrCdtTrf().getCdtTrfTxInf().get(0);
        assertThat(txn.getPmtId().getUETR()).isNotBlank();
        assertThat(txn.getPmtId().getEndToEndId()).isEqualTo("E2E-0001");
        assertThat(txn.getIntrBkSttlmAmt().getValue()).isEqualByComparingTo("1000.00");
        assertThat(txn.getIntrBkSttlmAmt().getCcy()).isEqualTo("EUR");
        // No intermediary agents supplied -> single-hop serial: InstdAgt falls back to the source CdtrAgt.
        assertThat(txn.getInstdAgt().getBICFI()).isEqualTo("DEUTDEFF");
        assertThat(doc.getFIToFICstmrCdtTrf().getGrpHdr().getSttlmInf().getSttlmMtd()).isEqualTo("CLRG");
    }

    @Test
    void coverModeProducesCorrelatedPacs008AndPacs009SharingUetr() {
        EnrichmentData enrichment = new EnrichmentData("CLEARED", null, null, null,
                List.of(new EnrichmentData.IntermediaryAgent("CHASUS33", "INTERMEDIARY1")), null);

        TransformationResult result = transformer.transform(loadSource(), enrichment, SettlementMethod.COVER);

        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages()).extracting(GeneratedMessage::messageId)
                .containsExactly("pacs.008.001.08", "pacs.009.001.08");

        var pacs008 = (com.bank.iso20022converter.model.pacs00800108.Document) result.messages().get(0).payload();
        var pacs009 = (com.bank.iso20022converter.model.pacs00900108.Document) result.messages().get(1).payload();

        String uetr008 = pacs008.getFIToFICstmrCdtTrf().getCdtTrfTxInf().get(0).getPmtId().getUETR();
        String uetr009 = pacs009.getFICdtTrf().getCdtTrfTxInf().get(0).getPmtId().getUETR();
        assertThat(uetr008).isEqualTo(uetr009);

        String e2e008 = pacs008.getFIToFICstmrCdtTrf().getCdtTrfTxInf().get(0).getPmtId().getEndToEndId();
        String e2e009 = pacs009.getFICdtTrf().getCdtTrfTxInf().get(0).getPmtId().getEndToEndId();
        assertThat(e2e008).isEqualTo(e2e009);

        assertThat(pacs009.getFICdtTrf().getGrpHdr().getSttlmInf().getSttlmMtd()).isEqualTo("COVE");
    }

    @Test
    void coverModeWithoutIntermediaryAgentsFailsLoudly() {
        assertThatThrownBy(() -> transformer.transform(loadSource(), cleared(), SettlementMethod.COVER))
                .isInstanceOf(MappingException.class)
                .satisfies(e -> assertThat(((MappingException) e).getErrorCode())
                        .isEqualTo(MappingException.MISSING_ENRICHMENT));
    }

    @Test
    void missingComplianceClearanceFailsLoudly() {
        EnrichmentData notCleared = new EnrichmentData(null, null, null, null, List.of(), null);
        assertThatThrownBy(() -> transformer.transform(loadSource(), notCleared, SettlementMethod.SERIAL))
                .isInstanceOf(MappingException.class)
                .satisfies(e -> assertThat(((MappingException) e).getErrorCode())
                        .isEqualTo(MappingException.MISSING_ENRICHMENT));
    }

    @Test
    void crossCurrencyWithoutExchangeRateFailsLoudly() {
        EnrichmentData missingRate = new EnrichmentData("CLEARED", null, "USD", null, List.of(), null);
        assertThatThrownBy(() -> transformer.transform(loadSource(), missingRate, SettlementMethod.SERIAL))
                .isInstanceOf(MappingException.class)
                .satisfies(e -> assertThat(((MappingException) e).getErrorCode())
                        .isEqualTo(MappingException.MISSING_ENRICHMENT));
    }

    @Test
    void crossCurrencyWithExchangeRateConverts() {
        EnrichmentData withRate = new EnrichmentData(
                "CLEARED", new BigDecimal("1.10"), "USD", null, List.of(), null);

        TransformationResult result = transformer.transform(loadSource(), withRate, SettlementMethod.SERIAL);
        var doc = (com.bank.iso20022converter.model.pacs00800108.Document) result.messages().get(0).payload();
        var amt = doc.getFIToFICstmrCdtTrf().getCdtTrfTxInf().get(0).getIntrBkSttlmAmt();

        assertThat(amt.getCcy()).isEqualTo("USD");
        assertThat(amt.getValue()).isEqualByComparingTo("1100.00");
    }
}
