package com.bank.iso20022converter.transform.impl;

import com.bank.iso20022converter.core.XmlDates;
import com.bank.iso20022converter.core.exception.MappingException;
import com.bank.iso20022converter.model.pacs00800108.ActiveCurrencyAndAmount;
import com.bank.iso20022converter.model.pacs00800108.CashAccount;
import com.bank.iso20022converter.model.pacs00800108.FinancialInstitution;
import com.bank.iso20022converter.model.pacs00800108.PostalAddress;
import com.bank.iso20022converter.model.pacs00800108.RemittanceInformation;
import com.bank.iso20022converter.model.pacs00800108.SettlementInstruction;
import com.bank.iso20022converter.transform.EnrichmentDefaults;
import com.bank.iso20022converter.transform.GeneratedMessage;
import com.bank.iso20022converter.transform.MappingNote;
import com.bank.iso20022converter.transform.MessageTransformer;
import com.bank.iso20022converter.transform.SettlementMethod;
import com.bank.iso20022converter.transform.TransformationResult;
import com.bank.iso20022converter.web.dto.EnrichmentData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.bank.iso20022converter.model.pain00100109.Document;
import com.bank.iso20022converter.model.pain00100109.CreditTransferTransaction;
import com.bank.iso20022converter.model.pain00100109.PaymentInstruction;

/**
 * pain.001.001.09 (customer payment initiation) -> pacs.008.001.08 (interbank credit
 * transfer), and, for {@link SettlementMethod#COVER}, additionally a correlated
 * pacs.009.001.08 cover message sharing the same UETR and end-to-end id.
 *
 * <p>This is the proof-of-concept transformer for the plan's real-world scenario: a
 * payment engine has already run posting/earmarking/flex-check/FX on the pain.001 and
 * calls this tool to produce the clearing-bound message(s), passing whatever those
 * steps decided as {@link EnrichmentData}.
 *
 * <p><b>Scope limitation (deliberate, not silent):</b> this transformer processes
 * exactly one payment instruction and one transaction per call. A pain.001 batch file
 * with multiple PmtInf/CdtTrfTxInf entries is rejected with a clear error rather than
 * silently processing only the first - batch fan-out is a known, documented gap (see
 * the plan), and the caller must split the file into per-transaction calls for now.
 */
@Component
public class Pain001ToClearingTransformer implements MessageTransformer<Document> {

    private static final String PACS008_NS = "urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08";
    private static final String PACS009_NS = "urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08";

    private final EnrichmentDefaults defaults;

    public Pain001ToClearingTransformer(EnrichmentDefaults defaults) {
        this.defaults = defaults;
    }

    @Override
    public String sourceMessageId() {
        return "pain.001.001.09";
    }

    @Override
    public Class<Document> sourceType() {
        return Document.class;
    }

    @Override
    public Set<String> supportedTargetMessageIds() {
        return Set.of("pacs.008.001.08", "pacs.009.001.08");
    }

    @Override
    public TransformationResult transform(Document source, EnrichmentData enrichment, SettlementMethod method) {
        if (enrichment == null) {
            enrichment = EnrichmentData.empty();
        }
        if (!"CLEARED".equals(enrichment.complianceStatus())) {
            throw new MappingException(MappingException.MISSING_ENRICHMENT,
                    "enrichment.complianceStatus must be exactly \"CLEARED\" before this tool will produce a "
                            + "clearing-bound message. This tool never assumes compliance clearance on the caller's behalf.");
        }

        List<PaymentInstruction> pmtInfs = source.getCstmrCdtTrfInitn().getPmtInf();
        if (pmtInfs.size() != 1) {
            throw new MappingException(MappingException.MAPPING_FAILED,
                    "This transformer processes exactly one PmtInf per call; source has " + pmtInfs.size()
                            + ". Batch fan-out is not yet supported - split the file into per-transaction calls.");
        }
        PaymentInstruction pmtInf = pmtInfs.get(0);
        List<CreditTransferTransaction> txns = pmtInf.getCdtTrfTxInf();
        if (txns.size() != 1) {
            throw new MappingException(MappingException.MAPPING_FAILED,
                    "This transformer processes exactly one CdtTrfTxInf per call; PmtInf '" + pmtInf.getPmtInfId()
                            + "' has " + txns.size() + ". Batch fan-out is not yet supported.");
        }
        CreditTransferTransaction txn = txns.get(0);

        List<MappingNote> notes = new ArrayList<>();

        String endToEndId = txn.getPmtId().getEndToEndId();
        String uetr = defaults.resolveOrGenerateUetr(txn.getPmtId().getUETR());
        notes.add(new MappingNote("PmtId/UETR",
                txn.getPmtId().getUETR() != null ? MappingNote.Action.COPIED : MappingNote.Action.GENERATED,
                txn.getPmtId().getUETR() != null
                        ? "Reused UETR already present on the source pain.001 transaction."
                        : "pain.001 carried no UETR; generated a fresh one. Retries of the same payment must "
                          + "reuse this UETR via enrichment - this tool is stateless and cannot detect duplicates."));

        ActiveCurrencyAndAmount settlementAmount = resolveSettlementAmount(txn, enrichment, notes);

        FinancialInstitution instgAgt = toFI(pmtInf.getDbtrAgt().getBICFI());
        notes.add(new MappingNote("InstgAgt", MappingNote.Action.COPIED,
                "pain.001 DbtrAgt (debtor's bank) mapped to InstgAgt for the first interbank leg."));

        FinancialInstitution instdAgt;
        FinancialInstitution intrmyAgt1 = null;
        List<EnrichmentData.IntermediaryAgent> agents = enrichment.intermediaryAgentsOrEmpty();
        if (method == SettlementMethod.COVER && agents.isEmpty()) {
            throw new MappingException(MappingException.MISSING_ENRICHMENT,
                    "settlementMethod=COVER requires enrichment.intermediaryAgents - this tool has no "
                            + "correspondent/BIC routing directory of its own.");
        }
        if (!agents.isEmpty()) {
            instdAgt = toFI(agents.get(0).bic());
            notes.add(new MappingNote("InstdAgt", MappingNote.Action.FROM_ENRICHMENT,
                    "Correspondent routing supplied by caller (payment engine owns BIC/correspondent lookup)."));
            if (agents.size() > 1) {
                intrmyAgt1 = toFI(agents.get(1).bic());
                notes.add(new MappingNote("IntrmyAgt1", MappingNote.Action.FROM_ENRICHMENT,
                        "Second correspondent agent supplied by caller."));
            }
        } else {
            instdAgt = toFI(txn.getCdtrAgt().getBICFI());
            notes.add(new MappingNote("InstdAgt", MappingNote.Action.COPIED,
                    "No intermediary agents supplied; mapped directly from pain.001 CdtrAgt (single-hop serial payment)."));
        }

        String chargeBearer = resolveChargeBearer(pmtInf, enrichment, notes);

        var pacs008Txn = new com.bank.iso20022converter.model.pacs00800108.CreditTransferTransaction();
        var pacs008PmtId = new com.bank.iso20022converter.model.pacs00800108.PaymentIdentification();
        pacs008PmtId.setInstrId(txn.getPmtId().getInstrId());
        pacs008PmtId.setEndToEndId(endToEndId);
        pacs008PmtId.setUETR(uetr);
        pacs008Txn.setPmtId(pacs008PmtId);
        pacs008Txn.setIntrBkSttlmAmt(settlementAmount);
        pacs008Txn.setIntrBkSttlmDt(XmlDates.copy(pmtInf.getReqdExctnDt()));
        notes.add(new MappingNote("IntrBkSttlmDt", MappingNote.Action.APPROXIMATED,
                "Copied from pain.001 ReqdExctnDt; the actual interbank settlement date is determined by the "
                        + "correspondent chain and may differ."));
        pacs008Txn.setChrgBr(chargeBearer);
        pacs008Txn.setInstgAgt(instgAgt);
        pacs008Txn.setInstdAgt(instdAgt);
        pacs008Txn.setIntrmyAgt1(intrmyAgt1);
        pacs008Txn.setDbtrAgt(instgAgt);
        pacs008Txn.setDbtr(toPacs008Party(pmtInf.getDbtr()));
        pacs008Txn.setDbtrAcct(toPacs008Account(pmtInf.getDbtrAcct()));
        pacs008Txn.setCdtrAgt(toFI(txn.getCdtrAgt().getBICFI()));
        pacs008Txn.setCdtr(toPacs008Party(txn.getCdtr()));
        pacs008Txn.setCdtrAcct(toPacs008Account(txn.getCdtrAcct()));
        pacs008Txn.setRmtInf(toPacs008RemittanceInfo(txn.getRmtInf(), enrichment, notes));

        var pacs008GrpHdr = new com.bank.iso20022converter.model.pacs00800108.GroupHeader();
        pacs008GrpHdr.setMsgId(source.getCstmrCdtTrfInitn().getGrpHdr().getMsgId() + "-CLR");
        notes.add(new MappingNote("GrpHdr/MsgId", MappingNote.Action.GENERATED,
                "Derived from the source pain.001 MsgId for traceability; this is a new outgoing message."));
        pacs008GrpHdr.setCreDtTm(XmlDates.nowDateTime());
        pacs008GrpHdr.setNbOfTxs("1");
        SettlementInstruction pacs008Sttlm = new SettlementInstruction();
        String settlementMethodCode = defaults.defaultSettlementMethodCode(method);
        pacs008Sttlm.setSttlmMtd(settlementMethodCode);
        notes.add(new MappingNote("GrpHdr/SttlmInf/SttlmMtd", MappingNote.Action.DEFAULTED,
                "No settlement method code in source; defaulted to '" + settlementMethodCode + "' for " + method + "."));
        pacs008GrpHdr.setSttlmInf(pacs008Sttlm);

        var pacs008Doc = new com.bank.iso20022converter.model.pacs00800108.Document();
        var pacs008Body = new com.bank.iso20022converter.model.pacs00800108.FIToFICustomerCreditTransferV08();
        pacs008Body.setGrpHdr(pacs008GrpHdr);
        pacs008Body.getCdtTrfTxInf().add(pacs008Txn);
        pacs008Doc.setFIToFICstmrCdtTrf(pacs008Body);

        List<GeneratedMessage> messages = new ArrayList<>();
        messages.add(new GeneratedMessage("pacs.008.001.08", pacs008Doc));

        if (method == SettlementMethod.COVER) {
            messages.add(new GeneratedMessage("pacs.009.001.08",
                    buildCoverMessage(pmtInf, txn, pacs008GrpHdr, pacs008Txn, notes)));
        }

        return new TransformationResult(messages, notes);
    }

    private com.bank.iso20022converter.model.pacs00900108.Document buildCoverMessage(
            PaymentInstruction pmtInf,
            CreditTransferTransaction sourceTxn,
            com.bank.iso20022converter.model.pacs00800108.GroupHeader pacs008GrpHdr,
            com.bank.iso20022converter.model.pacs00800108.CreditTransferTransaction pacs008Txn,
            List<MappingNote> notes) {

        var pacs009PmtId = new com.bank.iso20022converter.model.pacs00900108.PaymentIdentification();
        pacs009PmtId.setEndToEndId(pacs008Txn.getPmtId().getEndToEndId());
        pacs009PmtId.setUETR(pacs008Txn.getPmtId().getUETR());
        notes.add(new MappingNote("pacs.009 PmtId/UETR", MappingNote.Action.COPIED,
                "Same UETR as the accompanying pacs.008 - required for correspondent-chain correlation of a cover payment."));

        var pacs009Txn = new com.bank.iso20022converter.model.pacs00900108.CreditTransferTransaction();
        pacs009Txn.setPmtId(pacs009PmtId);
        var pacs009Amt = new com.bank.iso20022converter.model.pacs00900108.ActiveCurrencyAndAmount();
        pacs009Amt.setValue(pacs008Txn.getIntrBkSttlmAmt().getValue());
        pacs009Amt.setCcy(pacs008Txn.getIntrBkSttlmAmt().getCcy());
        pacs009Txn.setIntrBkSttlmAmt(pacs009Amt);
        pacs009Txn.setIntrBkSttlmDt(XmlDates.copy(pacs008Txn.getIntrBkSttlmDt()));
        pacs009Txn.setInstgAgt(toPacs009FI(pacs008Txn.getInstgAgt().getBICFI()));
        pacs009Txn.setInstdAgt(toPacs009FI(pacs008Txn.getInstdAgt().getBICFI()));
        if (pacs008Txn.getIntrmyAgt1() != null) {
            pacs009Txn.setIntrmyAgt1(toPacs009FI(pacs008Txn.getIntrmyAgt1().getBICFI()));
        }
        pacs009Txn.setDbtrAgt(toPacs009FI(pacs008Txn.getDbtrAgt().getBICFI()));
        pacs009Txn.setCdtrAgt(toPacs009FI(pacs008Txn.getCdtrAgt().getBICFI()));

        var underlying = new com.bank.iso20022converter.model.pacs00900108.UnderlyingCustomerCreditTransfer();
        underlying.setDbtr(toPacs009Party(pmtInf.getDbtr()));
        underlying.setCdtr(toPacs009Party(sourceTxn.getCdtr()));
        if (sourceTxn.getRmtInf() != null) {
            var rmtInf = new com.bank.iso20022converter.model.pacs00900108.RemittanceInformation();
            rmtInf.getUstrd().addAll(sourceTxn.getRmtInf().getUstrd());
            underlying.setRmtInf(rmtInf);
        }
        pacs009Txn.setUndrlygCstmrCdtTrf(underlying);

        var pacs009GrpHdr = new com.bank.iso20022converter.model.pacs00900108.GroupHeader();
        pacs009GrpHdr.setMsgId(pacs008GrpHdr.getMsgId() + "-COV");
        pacs009GrpHdr.setCreDtTm(XmlDates.copy(pacs008GrpHdr.getCreDtTm()));
        var pacs009Sttlm = new com.bank.iso20022converter.model.pacs00900108.SettlementInstruction();
        pacs009Sttlm.setSttlmMtd("COVE");
        pacs009GrpHdr.setSttlmInf(pacs009Sttlm);

        var pacs009Body = new com.bank.iso20022converter.model.pacs00900108.FinancialInstitutionCreditTransferV08();
        pacs009Body.setGrpHdr(pacs009GrpHdr);
        pacs009Body.getCdtTrfTxInf().add(pacs009Txn);

        var pacs009Doc = new com.bank.iso20022converter.model.pacs00900108.Document();
        pacs009Doc.setFICdtTrf(pacs009Body);
        return pacs009Doc;
    }

    private ActiveCurrencyAndAmount resolveSettlementAmount(
            CreditTransferTransaction txn, EnrichmentData enrichment, List<MappingNote> notes) {
        var instdAmt = txn.getAmt().getInstdAmt();
        String sourceCcy = instdAmt.getCcy();
        String targetCcy = enrichment.settlementCurrency();

        ActiveCurrencyAndAmount amount = new ActiveCurrencyAndAmount();
        if (targetCcy == null || targetCcy.equals(sourceCcy)) {
            amount.setValue(instdAmt.getValue());
            amount.setCcy(sourceCcy);
            notes.add(new MappingNote("IntrBkSttlmAmt", MappingNote.Action.COPIED,
                    "Same-currency payment; pain.001 InstdAmt copied through unchanged."));
            return amount;
        }

        if (enrichment.exchangeRate() == null) {
            throw new MappingException(MappingException.MISSING_ENRICHMENT,
                    "Cross-currency payment (source " + sourceCcy + " -> settlement " + targetCcy
                            + ") requires enrichment.exchangeRate; this tool never assumes an FX rate.");
        }
        BigDecimal converted = instdAmt.getValue()
                .multiply(enrichment.exchangeRate())
                .setScale(2, RoundingMode.HALF_UP);
        amount.setValue(converted);
        amount.setCcy(targetCcy);
        notes.add(new MappingNote("IntrBkSttlmAmt", MappingNote.Action.APPROXIMATED,
                "Converted " + sourceCcy + " -> " + targetCcy + " at caller-supplied exchangeRate "
                        + enrichment.exchangeRate() + "."));
        return amount;
    }

    private String resolveChargeBearer(PaymentInstruction pmtInf, EnrichmentData enrichment, List<MappingNote> notes) {
        if (enrichment.chargeBearer() != null && !enrichment.chargeBearer().isBlank()) {
            notes.add(new MappingNote("ChrgBr", MappingNote.Action.FROM_ENRICHMENT, "Charge bearer supplied by caller."));
            return enrichment.chargeBearer();
        }
        if (pmtInf.getChrgBr() != null && !pmtInf.getChrgBr().isBlank()) {
            notes.add(new MappingNote("ChrgBr", MappingNote.Action.APPROXIMATED,
                    "Copied from pain.001 ChrgBr; customer-level and interbank-level charge-bearer semantics differ slightly."));
            return pmtInf.getChrgBr();
        }
        notes.add(new MappingNote("ChrgBr", MappingNote.Action.DEFAULTED, "Not specified anywhere; defaulted to SHAR."));
        return "SHAR";
    }

    private RemittanceInformation toPacs008RemittanceInfo(
            com.bank.iso20022converter.model.pain00100109.RemittanceInformation source,
            EnrichmentData enrichment,
            List<MappingNote> notes) {
        List<String> lines = new ArrayList<>();
        if (source != null) {
            lines.addAll(source.getUstrd());
        }
        if (enrichment.earmarkReference() != null && !enrichment.earmarkReference().isBlank()) {
            lines.add("EARMARK-REF:" + enrichment.earmarkReference());
            notes.add(new MappingNote("RmtInf/Ustrd", MappingNote.Action.FROM_ENRICHMENT,
                    "earmarkReference appended for audit trail."));
        }
        if (lines.isEmpty()) {
            return null;
        }
        RemittanceInformation rmtInf = new RemittanceInformation();
        rmtInf.getUstrd().addAll(lines);
        return rmtInf;
    }

    private FinancialInstitution toFI(String bic) {
        FinancialInstitution fi = new FinancialInstitution();
        fi.setBICFI(bic);
        return fi;
    }

    private com.bank.iso20022converter.model.pacs00900108.FinancialInstitution toPacs009FI(String bic) {
        var fi = new com.bank.iso20022converter.model.pacs00900108.FinancialInstitution();
        fi.setBICFI(bic);
        return fi;
    }

    private com.bank.iso20022converter.model.pacs00800108.PartyIdentification toPacs008Party(
            com.bank.iso20022converter.model.pain00100109.PartyIdentification source) {
        var party = new com.bank.iso20022converter.model.pacs00800108.PartyIdentification();
        party.setNm(source.getNm());
        if (source.getPstlAdr() != null) {
            party.setPstlAdr(toPacs008Address(source.getPstlAdr()));
        }
        return party;
    }

    private com.bank.iso20022converter.model.pacs00900108.PartyIdentification toPacs009Party(
            com.bank.iso20022converter.model.pain00100109.PartyIdentification source) {
        var party = new com.bank.iso20022converter.model.pacs00900108.PartyIdentification();
        party.setNm(source.getNm());
        if (source.getPstlAdr() != null) {
            var addr = new com.bank.iso20022converter.model.pacs00900108.PostalAddress();
            var src = source.getPstlAdr();
            addr.setStrtNm(src.getStrtNm());
            addr.setBldgNb(src.getBldgNb());
            addr.setPstCd(src.getPstCd());
            addr.setTwnNm(src.getTwnNm());
            addr.setCtry(src.getCtry());
            addr.getAdrLine().addAll(src.getAdrLine());
            party.setPstlAdr(addr);
        }
        return party;
    }

    private PostalAddress toPacs008Address(com.bank.iso20022converter.model.pain00100109.PostalAddress source) {
        PostalAddress addr = new PostalAddress();
        addr.setStrtNm(source.getStrtNm());
        addr.setBldgNb(source.getBldgNb());
        addr.setPstCd(source.getPstCd());
        addr.setTwnNm(source.getTwnNm());
        addr.setCtry(source.getCtry());
        addr.getAdrLine().addAll(source.getAdrLine());
        return addr;
    }

    private CashAccount toPacs008Account(com.bank.iso20022converter.model.pain00100109.CashAccount source) {
        if (source == null) {
            return null;
        }
        CashAccount acct = new CashAccount();
        acct.setIBAN(source.getIBAN());
        return acct;
    }
}
