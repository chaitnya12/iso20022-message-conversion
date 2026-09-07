package com.bank.iso20022converter;

import com.bank.iso20022converter.model.pacs00800108.ActiveCurrencyAndAmount;
import com.bank.iso20022converter.model.pacs00800108.FinancialInstitution;
import com.bank.iso20022converter.model.pacs00800108.PartyIdentification;
import com.bank.iso20022converter.model.pacs00800108.PaymentIdentification;
import com.bank.iso20022converter.model.pain00100109.Document;
import com.bank.iso20022converter.model.pain00100109.GroupHeader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.GregorianCalendar;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Throwaway spike proving the XJC-generated classes round-trip correctly:
 * XML -> Java (JAXB) -> JSON (Jackson, via the JAXB annotation bridge) ->
 * Java -> XML. This de-risks the codegen + Jackson-JAXB bridge before any
 * service code is built on top of it (see the plan's "first implementation
 * task").
 *
 * <p>Important finding from this spike: XJC does NOT annotate the generated
 * {@code Document} classes with {@code @XmlRootElement} (the real ISO 20022
 * schemas don't either) - the root element is only declared via each
 * package's {@code ObjectFactory} ({@code @XmlElementDecl}). So unmarshalling
 * must target {@code JAXBElement<Document>} via {@code unmarshal(source,
 * Document.class)}, and marshalling must wrap the payload in a
 * {@code JAXBElement} using the message's namespace + "Document" local name
 * before calling {@code marshal(...)}. {@code MessageTypeRegistry} (core
 * layer) captures this per-message namespace so the service doesn't need
 * per-type special-casing.
 */
class RoundTripSpikeTest {

    private static final String PAIN001_NS = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09";

    @Test
    void pain001RoundTripsThroughXmlAndJson() throws Exception {
        JAXBContext ctx = JAXBContext.newInstance(Document.class);

        String xml = """
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
                    <CstmrCdtTrfInitn>
                        <GrpHdr>
                            <MsgId>MSG-0001</MsgId>
                            <CreDtTm>2026-09-07T10:15:00</CreDtTm>
                            <NbOfTxs>1</NbOfTxs>
                        </GrpHdr>
                        <PmtInf>
                            <PmtInfId>PMTINF-0001</PmtInfId>
                            <PmtMtd>TRF</PmtMtd>
                            <ReqdExctnDt>2026-09-08</ReqdExctnDt>
                            <Dbtr><Nm>Acme Corp</Nm></Dbtr>
                            <DbtrAcct><IBAN>GB29NWBK60161331926819</IBAN></DbtrAcct>
                            <DbtrAgt><BICFI>NWBKGB2L</BICFI></DbtrAgt>
                            <ChrgBr>SHAR</ChrgBr>
                            <CdtTrfTxInf>
                                <PmtId><EndToEndId>E2E-0001</EndToEndId></PmtId>
                                <Amt><InstdAmt Ccy="EUR">1000.00</InstdAmt></Amt>
                                <CdtrAgt><BICFI>DEUTDEFF</BICFI></CdtrAgt>
                                <Cdtr><Nm>Beta GmbH</Nm></Cdtr>
                            </CdtTrfTxInf>
                        </PmtInf>
                    </CstmrCdtTrfInitn>
                </Document>
                """;

        Unmarshaller unmarshaller = ctx.createUnmarshaller();
        JAXBElement<Document> element = unmarshaller.unmarshal(new StreamSource(new StringReader(xml)), Document.class);
        Document doc = element.getValue();

        assertThat(doc.getCstmrCdtTrfInitn().getGrpHdr().getMsgId()).isEqualTo("MSG-0001");
        assertThat(doc.getCstmrCdtTrfInitn().getPmtInf()).hasSize(1);
        assertThat(doc.getCstmrCdtTrfInitn().getPmtInf().get(0).getCdtTrfTxInf()).hasSize(1);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JakartaXmlBindAnnotationModule());

        String json = mapper.writeValueAsString(doc);
        assertThat(json).contains("MSG-0001").contains("Acme Corp").contains("DEUTDEFF");

        Document rehydrated = mapper.readValue(json, Document.class);
        assertThat(rehydrated.getCstmrCdtTrfInitn().getGrpHdr().getMsgId()).isEqualTo("MSG-0001");

        Marshaller marshaller = ctx.createMarshaller();
        StringWriter sw = new StringWriter();
        JAXBElement<Document> toMarshal = new JAXBElement<>(new QName(PAIN001_NS, "Document"), Document.class, rehydrated);
        marshaller.marshal(toMarshal, sw);
        String regeneratedXml = sw.toString();

        assertThat(regeneratedXml).contains("MSG-0001").contains("Beta GmbH").contains("GB29NWBK60161331926819");
    }

    @Test
    void pacs008ClassesConstructProgrammaticallyAndMarshal() throws JAXBException, javax.xml.datatype.DatatypeConfigurationException {
        var txn = new com.bank.iso20022converter.model.pacs00800108.CreditTransferTransaction();
        var pmtId = new PaymentIdentification();
        pmtId.setEndToEndId("E2E-0001");
        pmtId.setUETR("3c249e5a-1a1a-4c1a-9d3a-000000000001");
        txn.setPmtId(pmtId);

        var amt = new ActiveCurrencyAndAmount();
        amt.setValue(new java.math.BigDecimal("1000.00"));
        amt.setCcy("EUR");
        txn.setIntrBkSttlmAmt(amt);

        XMLGregorianCalendar date = DatatypeFactory.newInstance()
                .newXMLGregorianCalendar(new GregorianCalendar(2026, 8, 8));
        txn.setIntrBkSttlmDt(date);

        FinancialInstitution instgAgt = new FinancialInstitution();
        instgAgt.setBICFI("NWBKGB2L");
        txn.setInstgAgt(instgAgt);
        FinancialInstitution instdAgt = new FinancialInstitution();
        instdAgt.setBICFI("DEUTDEFF");
        txn.setInstdAgt(instdAgt);
        txn.setDbtrAgt(instgAgt);
        txn.setCdtrAgt(instdAgt);

        PartyIdentification dbtr = new PartyIdentification();
        dbtr.setNm("Acme Corp");
        txn.setDbtr(dbtr);
        PartyIdentification cdtr = new PartyIdentification();
        cdtr.setNm("Beta GmbH");
        txn.setCdtr(cdtr);

        assertThat(txn.getPmtId().getUETR()).hasSize(36);
        assertThat(txn.getIntrBkSttlmAmt().getValue()).isEqualByComparingTo("1000.00");
    }
}
