package com.cp.ecommerce.adapter.ai.order;

import java.util.ArrayList;
import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.DuplicateOrderCheckResult;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.DetectDuplicateOrderOutPort;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link DetectDuplicateOrderOutPort} backed by the same locally-hosted Ollama embedding model
 * (nomic-embed-text) that already grounds the support assistant's RAG lookups (see ADR 0020, ADR 0024) - the sixth AI feature,
 * and the first to use embedding-based semantic similarity rather than a chat-model classification/generation call.
 *
 * <p>
 * Two fast paths avoid a model call entirely, mirroring the blank-remarks short-circuit already used by
 * {@code OrderRemarksClassifierAdapter} and {@code RemarksLanguageDetectorAdapter}:
 * <ul>
 * <li>If this order's remarks are blank, the only meaningful signal left is "same customer, nothing written, submitted within
 * the lookback window" - if any candidate also has blank remarks, that alone is flagged (score {@code 1.0}, no ambiguity to
 * resolve), otherwise there is nothing to compare and the result is {@link DuplicateOrderCheckResult#none()}.</li>
 * <li>If every candidate's remarks are blank but this order's aren't, there is nothing to embed against, so the result is
 * {@link DuplicateOrderCheckResult#none()} without calling the model.</li>
 * </ul>
 * Otherwise, this order's remarks and every non-blank candidate's remarks are embedded in a single batched call, and the
 * candidate with the highest cosine similarity is flagged if it meets {@code service.ai.duplicate-order.similarity-threshold}.
 * Like {@link RemarksLanguageDetectorAdapter}, a failed or unreachable model call is not left to propagate: this runs
 * synchronously inside a best-effort saga step, so any failure defaults to {@link DuplicateOrderCheckResult#none()} rather than
 * risking the step (or, worse, letting a resilience-related exception be mistaken for a genuine duplicate).
 */
@Slf4j
@WebAdapter
@RequiredArgsConstructor
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "true")
public class DuplicateOrderDetectorAdapter implements DetectDuplicateOrderOutPort {

    private static final String RESILIENCE_INSTANCE_NAME = "detectDuplicateOrder";

    private final EmbeddingModel embeddingModel;

    private final ResilientExecutor resilientExecutor;

    @Value("${service.ai.duplicate-order.similarity-threshold:0.97}")
    private double similarityThreshold = 0.97;

    @Override
    public DuplicateOrderCheckResult check(final Order order, final List<Order> recentOrders) {

        if (!StringUtils.hasText(order.getRemarks())) {
            return recentOrders.stream()
                    .filter(candidate -> !StringUtils.hasText(candidate.getRemarks()))
                    .findFirst()
                    .map(
                            candidate -> flagged(
                                    candidate.getOrderNumber(),
                                    1.0,
                                    "Same customer placed order " + candidate.getOrderNumber()
                                            + " with no distinguishing remarks within the lookback window."))
                    .orElseGet(DuplicateOrderCheckResult::none);
        }

        final List<Order> comparableCandidates = recentOrders.stream()
                .filter(candidate -> StringUtils.hasText(candidate.getRemarks()))
                .toList();
        if (comparableCandidates.isEmpty()) {
            return DuplicateOrderCheckResult.none();
        }

        try {
            return resilientExecutor
                    .callResilient(RESILIENCE_INSTANCE_NAME, () -> compareWithModel(order, comparableCandidates));
        } catch (Exception exception) {
            log.warn(
                    "Could not run AI duplicate-order similarity check via Ollama, treating order as not a duplicate: {}",
                    order.getOrderNumber(),
                    exception);
            return DuplicateOrderCheckResult.none();
        }
    }

    private DuplicateOrderCheckResult compareWithModel(final Order order, final List<Order> comparableCandidates) {

        final List<String> texts = new ArrayList<>(comparableCandidates.size() + 1);
        texts.add(order.getRemarks());
        comparableCandidates.forEach(candidate -> texts.add(candidate.getRemarks()));
        final List<float[]> embeddings = embeddingModel.embed(texts);
        final float[] target = embeddings.get(0);

        double bestScore = 0;
        Order bestMatch = null;
        for (int i = 0; i < comparableCandidates.size(); i++) {
            final double score = cosineSimilarity(target, embeddings.get(i + 1));
            if (score > bestScore) {
                bestScore = score;
                bestMatch = comparableCandidates.get(i);
            }
        }

        if (bestMatch != null && bestScore >= similarityThreshold) {
            return flagged(
                    bestMatch.getOrderNumber(),
                    bestScore,
                    "Remarks are semantically near-identical to order " + bestMatch.getOrderNumber()
                            + " placed by the same customer within the lookback window.");
        }
        return DuplicateOrderCheckResult.none();
    }

    private static DuplicateOrderCheckResult flagged(
            final String matchedOrderNumber,
            final double score,
            final String rationale) {

        return DuplicateOrderCheckResult.builder()
                .duplicate(true)
                .matchedOrderNumber(matchedOrderNumber)
                .similarityScore(score)
                .rationale(rationale)
                .build();
    }

    // PMD's UseVarargs suggestion doesn't apply here: these are two distinct, same-length embedding vectors compared
    // pairwise, not a variable-length argument list - varargs would incorrectly let callers pass any number of vectors.
    @SuppressWarnings("PMD.UseVarargs")
    private static double cosineSimilarity(final float[] first, final float[] second) {

        double dotProduct = 0;
        double firstNorm = 0;
        double secondNorm = 0;
        for (int i = 0; i < first.length; i++) {
            dotProduct += first[i] * second[i];
            firstNorm += first[i] * first[i];
            secondNorm += second[i] * second[i];
        }
        if (firstNorm == 0 || secondNorm == 0) {
            return 0;
        }
        return dotProduct / (Math.sqrt(firstNorm) * Math.sqrt(secondNorm));
    }

}
