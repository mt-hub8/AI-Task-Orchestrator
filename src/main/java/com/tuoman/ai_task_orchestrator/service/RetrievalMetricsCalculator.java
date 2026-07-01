package com.tuoman.ai_task_orchestrator.service;

import com.tuoman.ai_task_orchestrator.dto.RetrievalMetricAtKResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RetrievalMetricsCalculator {

    public List<RetrievalMetricAtKResponse> calculate(
            List<Long> expectedChunkIds,
            List<Long> retrievedChunkIds,
            List<Integer> topKValues
    ) {
        Set<Long> expected = new LinkedHashSet<>(expectedChunkIds == null ? List.of() : expectedChunkIds);
        List<Long> retrieved = retrievedChunkIds == null ? List.of() : retrievedChunkIds;

        return topKValues.stream()
                .map(k -> calculateAtK(expected, retrieved, k))
                .toList();
    }

    private RetrievalMetricAtKResponse calculateAtK(Set<Long> expected, List<Long> retrieved, int k) {
        List<Long> topK = retrieved.stream()
                .limit(k)
                .toList();

        long hitCount = topK.stream()
                .filter(expected::contains)
                .count();

        double recallAtK = expected.isEmpty() ? 0.0 : (double) hitCount / expected.size();
        double hitRateAtK = hitCount > 0 ? 1.0 : 0.0;
        double mrr = reciprocalRank(expected, topK);
        double contextPrecisionAtK = topK.isEmpty() ? 0.0 : (double) hitCount / topK.size();

        return new RetrievalMetricAtKResponse(
                k,
                recallAtK,
                hitRateAtK,
                mrr,
                contextPrecisionAtK
        );
    }

    private double reciprocalRank(Set<Long> expected, List<Long> topK) {
        for (int i = 0; i < topK.size(); i++) {
            if (expected.contains(topK.get(i))) {
                return 1.0 / (i + 1);
            }
        }

        return 0.0;
    }
}
