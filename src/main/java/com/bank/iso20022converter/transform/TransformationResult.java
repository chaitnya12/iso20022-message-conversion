package com.bank.iso20022converter.transform;

import java.util.List;

/**
 * @param messages one or more correlated output messages (two for a cover-method
 *                 payment, one otherwise) - never empty
 * @param notes    every non-obvious mapping decision made (generated/defaulted/
 *                 approximated/copied-from-enrichment fields), for caller transparency
 */
public record TransformationResult(List<GeneratedMessage> messages, List<MappingNote> notes) {
}
