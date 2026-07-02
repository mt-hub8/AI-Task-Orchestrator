package com.tuoman.ai_task_orchestrator.evaluation.rag;

import com.tuoman.ai_task_orchestrator.dto.DocumentSearchRequest;
import com.tuoman.ai_task_orchestrator.dto.DocumentSearchResultResponse;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingProvider;
import com.tuoman.ai_task_orchestrator.service.DocumentEmbeddingService;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class RagRetrievalEvaluationExecutor {

    private static final int CONTENT_SNIPPET_MAX_LENGTH = 200;

    private final DocumentEmbeddingService documentEmbeddingService;

    private final EmbeddingProvider embeddingProvider;

    private final VectorStore vectorStore;

    private final RagRetrievalMetricsCalculator metricsCalculator;

    public RagRetrievalEvaluationExecutor(
            DocumentEmbeddingService documentEmbeddingService,
            EmbeddingProvider embeddingProvider,
            VectorStore vectorStore,
            RagRetrievalMetricsCalculator metricsCalculator
    ) {
        this.documentEmbeddingService = documentEmbeddingService;
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.metricsCalculator = metricsCalculator;
    }

    public RagRetrievalEvaluationReport evaluate(
            RagRetrievalEvaluationDataset dataset,
            String datasetPath,
            int fallbackTopK,
            Long documentId
    ) {
        List<RagRetrievalCaseResult> caseResults = new ArrayList<>();
        int datasetDefaultTopK = dataset.defaultTopK() == null ? fallbackTopK : dataset.defaultTopK();

        for (RagRetrievalEvaluationCase evaluationCase : dataset.cases()) {
            int topK = normalizeTopK(evaluationCase.topK(), datasetDefaultTopK, fallbackTopK);
            long startedAt = System.nanoTime();
            List<RagRetrievedItem> retrievedItems = search(evaluationCase.query(), topK, documentId);
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;

            List<RagRetrievalExpectedItem> matchedExpectedItems = evaluationCase.expectedItems().stream()
                    .filter(expected -> retrievedItems.stream().anyMatch(retrieved -> metricsCalculator.matches(expected, retrieved)))
                    .toList();

            RagCaseMetrics metrics = metricsCalculator.calculate(
                    evaluationCase.expectedItems(),
                    matchedExpectedItems,
                    retrievedItems
            );

            caseResults.add(new RagRetrievalCaseResult(
                    evaluationCase.caseId(),
                    evaluationCase.query(),
                    topK,
                    evaluationCase.expectedItems(),
                    retrievedItems,
                    matchedExpectedItems,
                    metrics.hit(),
                    metrics.recallAtK(),
                    metrics.precisionAtK(),
                    metrics.rrAtK(),
                    latencyMs
            ));
        }

        RagRetrievalSummaryMetrics summary = summarize(caseResults);
        return new RagRetrievalEvaluationReport(
                dataset.datasetName(),
                datasetPath,
                Instant.now(),
                datasetDefaultTopK,
                embeddingProvider.provider(),
                embeddingProvider.model(),
                embeddingProvider.dimension(),
                vectorStore.getClass().getSimpleName(),
                summary,
                caseResults
        );
    }

    private List<RagRetrievedItem> search(String query, int topK, Long documentId) {
        DocumentSearchRequest searchRequest = new DocumentSearchRequest();
        searchRequest.setQuery(query);
        searchRequest.setTopK(topK);
        searchRequest.setDocumentId(documentId);

        List<DocumentSearchResultResponse> results = documentEmbeddingService.search(searchRequest);
        List<RagRetrievedItem> items = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            DocumentSearchResultResponse result = results.get(i);
            items.add(new RagRetrievedItem(
                    i + 1,
                    result.getDocumentId(),
                    result.getHeadingPath(),
                    result.getChunkId(),
                    result.getScore(),
                    contentSnippet(result.getContent())
            ));
        }
        return items;
    }

    private RagRetrievalSummaryMetrics summarize(List<RagRetrievalCaseResult> cases) {
        int totalCases = cases.size();
        int hitCount = (int) cases.stream().filter(RagRetrievalCaseResult::hit).count();

        double hitRate = totalCases == 0 ? 0.0 : (double) hitCount / totalCases;
        double averageRecall = average(cases.stream().map(RagRetrievalCaseResult::recallAtK).toList());
        double averagePrecision = average(cases.stream().map(RagRetrievalCaseResult::precisionAtK).toList());
        double mrr = average(cases.stream().map(RagRetrievalCaseResult::rrAtK).toList());
        double averageLatency = average(cases.stream().map(c -> (double) c.latencyMs()).toList());

        return new RagRetrievalSummaryMetrics(
                totalCases,
                hitCount,
                hitRate,
                averageRecall,
                averagePrecision,
                mrr,
                averageLatency
        );
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private int normalizeTopK(Integer caseTopK, int datasetDefaultTopK, int fallbackTopK) {
        if (caseTopK != null && caseTopK > 0) {
            return caseTopK;
        }
        if (datasetDefaultTopK > 0) {
            return datasetDefaultTopK;
        }
        return Math.max(fallbackTopK, 1);
    }

    private String contentSnippet(String content) {
        if (content == null) {
            return null;
        }
        if (content.length() <= CONTENT_SNIPPET_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, CONTENT_SNIPPET_MAX_LENGTH);
    }
}
