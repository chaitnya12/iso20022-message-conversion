package com.bank.iso20022converter.validation;

import com.bank.iso20022converter.model.pacs00800108.ActiveCurrencyAndAmount;
import com.bank.iso20022converter.model.pacs00800108.CreditTransferTransaction;
import com.bank.iso20022converter.model.pacs00800108.Document;
import com.bank.iso20022converter.model.pacs00800108.FIToFICustomerCreditTransferV08;
import com.bank.iso20022converter.model.pacs00800108.FinancialInstitution;
import com.bank.iso20022converter.model.pacs00800108.GroupHeader;
import com.bank.iso20022converter.model.pacs00800108.PartyIdentification;
import com.bank.iso20022converter.model.pacs00800108.PaymentIdentification;
import com.bank.iso20022converter.model.pacs00800108.PostalAddress;
import com.bank.iso20022converter.model.pacs00800108.SettlementInstruction;
import com.bank.iso20022converter.core.XmlDates;
import com.bank.iso20022converter.transform.GeneratedMessage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CbprPlusValidatorTest {

    private final CbprPlusValidator validator = new CbprPlusValidator();

    private Document validPacs008() {
        FinancialInstitution nwbk = fi("NWBKGB2L");
        FinancialInstitution deutd = fi("DEUTDEFF");

        PostalAddress dbtrAddr = new PostalAddress();
        dbtrAddr.setCtry("US");
        dbtrAddr.setStrtNm("Market Street");
        PartyIdentification dbtr = new PartyIdentification();
        dbtr.setNm("Acme Corp");
        dbtr.setPstlAdr(dbtrAddr);

        PostalAddress cdtrAddr = new PostalAddress();
        cdtrAddr.setCtry("DE");
        cdtrAddr.setTwnNm("Frankfurt");
        PartyIdentification cdtr = new PartyIdentification();
        cdtr.setNm("Beta GmbH");
        cdtr.setPstlAdr(cdtrAddr);

        PaymentIdentification pmtId = new PaymentIdentification();
        pmtId.setEndToEndId("E2E-0001");
        pmtId.setUETR("3c249e5a-1a1a-4c1a-9d3a-000000000001");

        ActiveCurrencyAndAmount amt = new ActiveCurrencyAndAmount();
        amt.setValue(new BigDecimal("1000.00"));
        amt.setCcy("EUR");

        CreditTransferTransaction txn = new CreditTransferTransaction();
        txn.setPmtId(pmtId);
        txn.setIntrBkSttlmAmt(amt);
        txn.setIntrBkSttlmDt(XmlDates.nowDateTime());
        txn.setInstgAgt(nwbk);
        txn.setInstdAgt(deutd);
        txn.setDbtrAgt(nwbk);
        txn.setCdtrAgt(deutd);
        txn.setDbtr(dbtr);
        txn.setCdtr(cdtr);

        GroupHeader grpHdr = new GroupHeader();
        grpHdr.setMsgId("MSG-CLR-0001");
        grpHdr.setCreDtTm(XmlDates.nowDateTime());
        grpHdr.setNbOfTxs("1");
        SettlementInstruction sttlm = new SettlementInstruction();
        sttlm.setSttlmMtd("CLRG");
        grpHdr.setSttlmInf(sttlm);

        FIToFICustomerCreditTransferV08 body = new FIToFICustomerCreditTransferV08();
        body.setGrpHdr(grpHdr);
        body.getCdtTrfTxInf().add(txn);

        Document doc = new Document();
        doc.setFIToFICstmrCdtTrf(body);
        return doc;
    }

    private FinancialInstitution fi(String bic) {
        FinancialInstitution fi = new FinancialInstitution();
        fi.setBICFI(bic);
        return fi;
    }

    @Test
    void validMessagePassesValidation() {
        assertThatCode(() -> validator.validate(new GeneratedMessage("pacs.008.001.08", validPacs008())))
                .doesNotThrowAnyException();
    }

    @Test
    void missingUetrIsRejected() {
        Document doc = validPacs008();
        doc.getFIToFICstmrCdtTrf().getCdtTrfTxInf().get(0).getPmtId().setUETR(null);

        assertThatThrownBy(() -> validator.validate(new GeneratedMessage("pacs.008.001.08", doc)))
                .isInstanceOf(CbprPlusViolationException.class)
                .satisfies(e -> assertThat(((CbprPlusViolationException) e).getViolations())
                        .anyMatch(v -> v.contains("UETR")));
    }

    @Test
    void malformedBicIsRejected() {
        Document doc = validPacs008();
        doc.getFIToFICstmrCdtTrf().getCdtTrfTxInf().get(0).getInstgAgt().setBICFI("bad-bic");

        assertThatThrownBy(() -> validator.validate(new GeneratedMessage("pacs.008.001.08", doc)))
                .isInstanceOf(CbprPlusViolationException.class)
                .satisfies(e -> assertThat(((CbprPlusViolationException) e).getViolations())
                        .anyMatch(v -> v.contains("BICFI")));
    }

    @Test
    void unstructuredAddressIsRejected() {
        Document doc = validPacs008();
        doc.getFIToFICstmrCdtTrf().getCdtTrfTxInf().get(0).getDbtr().setPstlAdr(null);

        assertThatThrownBy(() -> validator.validate(new GeneratedMessage("pacs.008.001.08", doc)))
                .isInstanceOf(CbprPlusViolationException.class)
                .satisfies(e -> assertThat(((CbprPlusViolationException) e).getViolations())
                        .anyMatch(v -> v.contains("structured postal address")));
    }
}
