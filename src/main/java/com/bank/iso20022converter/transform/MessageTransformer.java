package com.bank.iso20022converter.transform;

import com.bank.iso20022converter.web.dto.EnrichmentData;

import java.util.Set;

/**
 * A plugin mapping one source ISO 20022 message type to one or more correlated target
 * types. Adding support for a new message-type pair means adding one new
 * {@code @Component}-annotated implementation of this interface - nothing else in the
 * system needs to change (see {@link TransformerRegistry}).
 *
 * <p>Deliberately keyed by source type only (not source+target): a single transformer
 * may produce different output sets depending on {@link SettlementMethod} (e.g. a
 * pain.001 source can yield just a pacs.008, or a correlated pacs.008+pacs.009 pair),
 * and building both outputs in one {@link #transform} call is what lets them share a
 * single, provably consistent UETR.
 *
 * @param <S> the JAXB-generated source "Document" type
 */
public interface MessageTransformer<S> {

    String sourceMessageId();

    Class<S> sourceType();

    /** Every target message type this transformer can produce, across all settlement methods. */
    Set<String> supportedTargetMessageIds();

    /**
     * @param enrichment caller-supplied business data (compliance status, FX rate,
     *                   correspondent routing, ...) this transformer needs but the
     *                   source message doesn't carry. Use {@link EnrichmentData#empty()}
     *                   for transformers that need none.
     * @param method     settlement method to produce; {@link SettlementMethod#NOT_APPLICABLE}
     *                   for transformers with no such concept (e.g. status-report mapping).
     * @throws com.bank.iso20022converter.core.exception.MappingException if a required
     *         enrichment field is missing, or the mapping otherwise cannot be completed
     */
    TransformationResult transform(S source, EnrichmentData enrichment, SettlementMethod method);
}
