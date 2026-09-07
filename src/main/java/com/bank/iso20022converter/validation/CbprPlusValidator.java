package com.bank.iso20022converter.validation;

import com.bank.iso20022converter.transform.GeneratedMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Approximates SWIFT's CBPR+ (Cross-Border Payments and Reporting Plus) usage
 * guideline constraints for the messages this project generates. These constraints
 * are NOT sourced from a licensed CBPR+ XSD (those require a SWIFT MyStandards
 * account) - they encode the publicly documented, most commonly cited CBPR+
 * requirements: a mandatory well-formed UETR, a structured (not purely free-text)
 * postal address, and valid-looking agent BICs. Do not represent a message that
 * passes these checks as CBPR+-certified; treat this as a best-effort realism layer,
 * not a conformance guarantee (see the plan's Risk #1).
 */
@Component
public class CbprPlusValidator {

    private static final Pattern UETR_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BIC_PATTERN = Pattern.compile("^[A-Z0-9]{8}([A-Z0-9]{3})?$");

    /** @throws CbprPlusViolationException if strict validation fails */
    public void validate(GeneratedMessage message) {
        List<String> violations = switch (message.messageId()) {
            case "pacs.008.001.08" ->
                    validatePacs008((com.bank.iso20022converter.model.pacs00800108.Document) message.payload());
            case "pacs.009.001.08" ->
                    validatePacs009((com.bank.iso20022converter.model.pacs00900108.Document) message.payload());
            case "pacs.002.001.10" ->
                    validatePacs002((com.bank.iso20022converter.model.pacs00200110.Document) message.payload());
            default -> List.of();
        };
        if (!violations.isEmpty()) {
            throw new CbprPlusViolationException(violations);
        }
    }

    /** For a cover payment, both legs must reference the same UETR - the whole point of the pattern. */
    public void validateCoverPairCorrelation(List<GeneratedMessage> messages) {
        String pacs008Uetr = messages.stream()
                .filter(m -> m.messageId().equals("pacs.008.001.08"))
                .map(m -> uetrOf(((com.bank.iso20022converter.model.pacs00800108.Document) m.payload())
                        .getFIToFICstmrCdtTrf().getCdtTrfTxInf().get(0)))
                .findFirst().orElse(null);
        String pacs009Uetr = messages.stream()
                .filter(m -> m.messageId().equals("pacs.009.001.08"))
                .map(m -> ((com.bank.iso20022converter.model.pacs00900108.Document) m.payload())
                        .getFICdtTrf().getCdtTrfTxInf().get(0).getPmtId().getUETR())
                .findFirst().orElse(null);
        if (pacs008Uetr != null && pacs009Uetr != null && !Objects.equals(pacs008Uetr, pacs009Uetr)) {
            throw new CbprPlusViolationException(List.of(
                    "Cover pair UETR mismatch: pacs.008 has " + pacs008Uetr + " but pacs.009 has " + pacs009Uetr));
        }
    }

    private String uetrOf(com.bank.iso20022converter.model.pacs00800108.CreditTransferTransaction txn) {
        return txn.getPmtId().getUETR();
    }

    private List<String> validatePacs008(com.bank.iso20022converter.model.pacs00800108.Document doc) {
        List<String> violations = new ArrayList<>();
        var txn = doc.getFIToFICstmrCdtTrf().getCdtTrfTxInf().get(0);

        checkUetr(txn.getPmtId().getUETR(), violations);
        checkBic("InstgAgt", txn.getInstgAgt() == null ? null : txn.getInstgAgt().getBICFI(), violations);
        checkBic("InstdAgt", txn.getInstdAgt() == null ? null : txn.getInstdAgt().getBICFI(), violations);
        checkBic("DbtrAgt", txn.getDbtrAgt() == null ? null : txn.getDbtrAgt().getBICFI(), violations);
        checkBic("CdtrAgt", txn.getCdtrAgt() == null ? null : txn.getCdtrAgt().getBICFI(), violations);
        checkStructuredAddress("Dbtr/PstlAdr", txn.getDbtr() == null ? null : txn.getDbtr().getPstlAdr(),
                violations, a -> a.getCtry(), a -> a.getStrtNm(), a -> a.getTwnNm());
        checkStructuredAddress("Cdtr/PstlAdr", txn.getCdtr() == null ? null : txn.getCdtr().getPstlAdr(),
                violations, a -> a.getCtry(), a -> a.getStrtNm(), a -> a.getTwnNm());
        return violations;
    }

    private List<String> validatePacs009(com.bank.iso20022converter.model.pacs00900108.Document doc) {
        List<String> violations = new ArrayList<>();
        var txn = doc.getFICdtTrf().getCdtTrfTxInf().get(0);

        checkUetr(txn.getPmtId().getUETR(), violations);
        checkBic("InstgAgt", txn.getInstgAgt() == null ? null : txn.getInstgAgt().getBICFI(), violations);
        checkBic("InstdAgt", txn.getInstdAgt() == null ? null : txn.getInstdAgt().getBICFI(), violations);
        return violations;
    }

    private List<String> validatePacs002(com.bank.iso20022converter.model.pacs00200110.Document doc) {
        List<String> violations = new ArrayList<>();
        for (var txn : doc.getFIToFIPmtStsRpt().getTxInfAndSts()) {
            checkUetr(txn.getOrgnlUETR(), violations);
        }
        return violations;
    }

    private void checkUetr(String uetr, List<String> violations) {
        if (uetr == null || !UETR_PATTERN.matcher(uetr).matches()) {
            violations.add("UETR is missing or not a well-formed UUID: '" + uetr + "'");
        }
    }

    private void checkBic(String field, String bic, List<String> violations) {
        if (bic == null || !BIC_PATTERN.matcher(bic).matches()) {
            violations.add(field + " BICFI is missing or not a valid 8/11-character BIC: '" + bic + "'");
        }
    }

    @FunctionalInterface
    private interface AddressField {
        String get(com.bank.iso20022converter.model.pacs00800108.PostalAddress address);
    }

    private void checkStructuredAddress(
            String field,
            com.bank.iso20022converter.model.pacs00800108.PostalAddress address,
            List<String> violations,
            AddressField country,
            AddressField street,
            AddressField town) {
        if (address == null || isBlank(country.get(address))
                || (isBlank(street.get(address)) && isBlank(town.get(address)))) {
            violations.add(field + " must be a structured postal address (Ctry plus StrtNm or TwnNm), "
                    + "not just free-text AdrLine - CBPR+ requires structured addresses.");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
