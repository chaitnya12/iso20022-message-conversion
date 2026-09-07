package com.bank.iso20022converter.transform.impl;

import com.bank.iso20022converter.TestFixtures;
import com.bank.iso20022converter.core.JaxbMessageCodec;
import com.bank.iso20022converter.core.MessageTypeRegistry;
import com.bank.iso20022converter.model.pacs00200110.Document;
import com.bank.iso20022converter.transform.GeneratedMessage;
import com.bank.iso20022converter.transform.SettlementMethod;
import com.bank.iso20022converter.transform.TransformationResult;
import com.bank.iso20022converter.web.dto.EnrichmentData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Pacs002ToPain002TransformerTest {

    private final MessageTypeRegistry registry = new MessageTypeRegistry();
    private final JaxbMessageCodec codec = new JaxbMessageCodec();
    private final Pacs002ToPain002Transformer transformer = new Pacs002ToPain002Transformer();

    @Test
    void rejectionStatusCarriesThroughToPain002() {
        Document source = (Document) codec.unmarshal(
                TestFixtures.pacs002RejectionSample(), registry.requireByMessageId("pacs.002.001.10"));

        TransformationResult result = transformer.transform(source, EnrichmentData.empty(), SettlementMethod.NOT_APPLICABLE);

        assertThat(result.messages()).hasSize(1);
        GeneratedMessage msg = result.messages().get(0);
        assertThat(msg.messageId()).isEqualTo("pain.002.001.10");

        var doc = (com.bank.iso20022converter.model.pain00200110.Document) msg.payload();
        var txn = doc.getCstmrPmtStsRpt().getOrgnlPmtInfAndSts().get(0).getTxInfAndSts().get(0);

        assertThat(txn.getOrgnlEndToEndId()).isEqualTo("E2E-0001");
        assertThat(txn.getOrgnlUETR()).isEqualTo("3c249e5a-1a1a-4c1a-9d3a-000000000001");
        assertThat(txn.getTxSts()).isEqualTo("RJCT");
        assertThat(txn.getStsRsnInf().getRsn()).isEqualTo("AC04");
        assertThat(txn.getStsRsnInf().getAddtlInf()).containsExactly("Account closed");
    }
}
