package com.bank.iso20022converter.transform.impl;

import com.bank.iso20022converter.core.XmlDates;
import com.bank.iso20022converter.core.exception.MappingException;
import com.bank.iso20022converter.model.pacs00200110.Document;
import com.bank.iso20022converter.model.pacs00200110.PaymentTransactionInformationAndStatus;
import com.bank.iso20022converter.model.pain00200110.OriginalGroupInformationAndStatus;
import com.bank.iso20022converter.model.pain00200110.OriginalPaymentInstructionAndStatus;
import com.bank.iso20022converter.model.pain00200110.StatusReasonInformation;
import com.bank.iso20022converter.transform.GeneratedMessage;
import com.bank.iso20022converter.transform.MappingNote;
import com.bank.iso20022converter.transform.MessageTransformer;
import com.bank.iso20022converter.transform.SettlementMethod;
import com.bank.iso20022converter.transform.TransformationResult;
import com.bank.iso20022converter.web.dto.EnrichmentData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Return-path mapping: pacs.002.001.10 (interbank status report from clearing) ->
 * pain.002.001.10 (customer status report back to the payment originator). No
 * enrichment is needed - this is a direct status/reason-code carry-through.
 *
 * <p>Stateless by design (see the plan): ISO 20022 already threads
 * OrgnlEndToEndId/OrgnlUETR end-to-end, so no correlation store is required to answer
 * "which of the originator's payments does this status belong to." What this tool
 * genuinely cannot recover without a store is the *original pain.001 message id and
 * PmtInfId* - pacs.002 doesn't carry them. Those fields are filled with a clearly
 * labeled best-effort placeholder and a mapping note, not silently invented.
 *
 * <p>Same single-transaction-per-call scope limitation as
 * {@link Pain001ToClearingTransformer}, for the same reason (documented batch fan-out
 * gap, not silent partial processing).
 */
@Component
public class Pacs002ToPain002Transformer implements MessageTransformer<Document> {

    @Override
    public String sourceMessageId() {
        return "pacs.002.001.10";
    }

    @Override
    public Class<Document> sourceType() {
        return Document.class;
    }

    @Override
    public Set<String> supportedTargetMessageIds() {
        return Set.of("pain.002.001.10");
    }

    @Override
    public TransformationResult transform(Document source, EnrichmentData enrichment, SettlementMethod method) {
        List<PaymentTransactionInformationAndStatus> txns = source.getFIToFIPmtStsRpt().getTxInfAndSts();
        if (txns.size() != 1) {
            throw new MappingException(MappingException.MAPPING_FAILED,
                    "This transformer processes exactly one TxInfAndSts per call; source has " + txns.size()
                            + ". Batch fan-out is not yet supported - split into per-transaction calls.");
        }
        PaymentTransactionInformationAndStatus srcTxn = txns.get(0);
        List<MappingNote> notes = new ArrayList<>();

        var targetTxn = new com.bank.iso20022converter.model.pain00200110.PaymentTransactionInformationAndStatus();
        targetTxn.setOrgnlInstrId(srcTxn.getOrgnlInstrId());
        targetTxn.setOrgnlEndToEndId(srcTxn.getOrgnlEndToEndId());
        targetTxn.setOrgnlUETR(srcTxn.getOrgnlUETR());
        targetTxn.setTxSts(srcTxn.getTxSts());
        notes.add(new MappingNote("TxInfAndSts/TxSts", MappingNote.Action.COPIED,
                "Status code carried through unchanged from the interbank status report."));
        if (srcTxn.getStsRsnInf() != null) {
            StatusReasonInformation reason = new StatusReasonInformation();
            reason.setRsn(srcTxn.getStsRsnInf().getRsn());
            reason.getAddtlInf().addAll(srcTxn.getStsRsnInf().getAddtlInf());
            targetTxn.setStsRsnInf(reason);
            notes.add(new MappingNote("TxInfAndSts/StsRsnInf", MappingNote.Action.COPIED,
                    "Reason code/details carried through unchanged."));
        }

        String orgnlPmtInfId = srcTxn.getOrgnlInstrId() != null
                ? srcTxn.getOrgnlInstrId()
                : "UNKNOWN-" + srcTxn.getOrgnlEndToEndId();
        notes.add(new MappingNote("OrgnlPmtInfAndSts/OrgnlPmtInfId", MappingNote.Action.APPROXIMATED,
                "pacs.002 does not carry the original pain.001 PmtInfId. Best-effort placeholder derived from "
                        + "OrgnlInstrId/OrgnlEndToEndId - this tool is stateless and keeps no correlation store; "
                        + "a payment engine that needs exact PmtInfId correlation must track it itself."));

        var pmtInfAndSts = new OriginalPaymentInstructionAndStatus();
        pmtInfAndSts.setOrgnlPmtInfId(orgnlPmtInfId);
        pmtInfAndSts.getTxInfAndSts().add(targetTxn);

        var orgnlGrpInfAndSts = new OriginalGroupInformationAndStatus();
        orgnlGrpInfAndSts.setOrgnlMsgId(source.getFIToFIPmtStsRpt().getGrpHdr().getMsgId());
        orgnlGrpInfAndSts.setOrgnlMsgNmId("pain.001.001.09");
        notes.add(new MappingNote("OrgnlGrpInfAndSts", MappingNote.Action.APPROXIMATED,
                "pacs.002 does not carry the original pain.001 message id either; OrgnlMsgId is best-effort set "
                        + "to this status report's own MsgId and OrgnlMsgNmId assumed to be pain.001.001.09."));

        var targetGrpHdr = new com.bank.iso20022converter.model.pain00200110.GroupHeader();
        targetGrpHdr.setMsgId(source.getFIToFIPmtStsRpt().getGrpHdr().getMsgId() + "-CST");
        targetGrpHdr.setCreDtTm(XmlDates.nowDateTime());
        notes.add(new MappingNote("GrpHdr/MsgId", MappingNote.Action.GENERATED,
                "Derived from the source pacs.002 MsgId for traceability; this is a new outgoing message."));

        var body = new com.bank.iso20022converter.model.pain00200110.CustomerPaymentStatusReportV10();
        body.setGrpHdr(targetGrpHdr);
        body.setOrgnlGrpInfAndSts(orgnlGrpInfAndSts);
        body.getOrgnlPmtInfAndSts().add(pmtInfAndSts);

        var doc = new com.bank.iso20022converter.model.pain00200110.Document();
        doc.setCstmrPmtStsRpt(body);

        return new TransformationResult(List.of(new GeneratedMessage("pain.002.001.10", doc)), notes);
    }
}
