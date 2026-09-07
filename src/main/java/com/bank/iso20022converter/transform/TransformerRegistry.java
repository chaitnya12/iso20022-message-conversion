package com.bank.iso20022converter.transform;

import com.bank.iso20022converter.web.dto.TransformerPairDescriptor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Collects every {@link MessageTransformer} bean Spring finds and indexes it by
 * source message id. Adding a new mapping pair requires no change here - just add a
 * new {@code @Component}-annotated {@link MessageTransformer} implementation.
 */
@Component
public class TransformerRegistry {

    private final Map<String, MessageTransformer<?>> bySourceMessageId;

    public TransformerRegistry(List<MessageTransformer<?>> transformers) {
        this.bySourceMessageId = transformers.stream()
                .collect(Collectors.toMap(MessageTransformer::sourceMessageId, Function.identity()));
    }

    public Optional<MessageTransformer<?>> find(String sourceMessageId) {
        return Optional.ofNullable(bySourceMessageId.get(sourceMessageId));
    }

    public List<TransformerPairDescriptor> availablePairs() {
        return bySourceMessageId.values().stream()
                .flatMap(t -> t.supportedTargetMessageIds().stream()
                        .map(target -> new TransformerPairDescriptor(t.sourceMessageId(), target)))
                .toList();
    }
}
