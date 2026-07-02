package com.tuoman.ai_task_orchestrator.evaluation.rag;

import com.tuoman.ai_task_orchestrator.dto.DocumentSearchRequest;
import com.tuoman.ai_task_orchestrator.dto.DocumentSearchResultResponse;
import com.tuoman.ai_task_orchestrator.rerank.RerankCandidate;
import com.tuoman.ai_task_orchestrator.rerank.RerankRequest;
import com.tuoman.ai_task_orchestrator.rerank.RerankResponse;
import com.tuoman.ai_task_orchestrator.rerank.Reranker;
import com.tuoman.ai_task_orchestrator.rerank.RerankedItem;
import com.tuoman.ai_task_orchestrator.service.DocumentEmbeddingService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RagEvaluationRetrievalHelper {

    private static final int CONTENT_SNIPPET_MAX_LENGTH = 200;

    public List<RagRetrievedItem> searchBaseline(
            DocumentEmbeddingService documentEmbeddingService,
            String query,
            int topK,
            Long documentId
    ) {
        return toRetrievedItems(search(documentEmbeddingService, query, topK, documentId), false);
    }

    public RerankSearchOutcome searchWithRerank(
            DocumentEmbeddingService documentEmbeddingService,
            Reranker reranker,
            String query,
            int finalTopK,
            int candidateTopK,
            Long documentId
    ) {
        List<DocumentSearchResultResponse> vectorResults = search(documentEmbeddingService, query, candidateTopK, documentId);
        List<RerankCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            DocumentSearchResultResponse result = vectorResults.get(i);
            candidates.add(new RerankCandidate(
                    i + 1,
                    result.getDocumentId(),
                    result.getHeadingPath(),
                    result.getChunkId(),
                    result.getContent(),
                    result.getScore()
            ));
        }

        RerankResponse rerankResponse = reranker.rerank(new RerankRequest(query, candidates, finalTopK));
        List<RagRetrievedItem> items = new ArrayList<>();
        for (RerankedItem item : rerankResponse.items()) {
            items.add(new RagRetrievedItem(
                    item.rerankedRank(),
                    item.documentId(),
                    item.documentTitle(),
                    item.chunkId(),
                    item.rerankScore(),
                    contentSnippet(item.content()),
                    item.originalRank(),
                    item.rerankedRank(),
                    item.originalScore(),
                    item.rerankScore()
            ));
        }
        return new RerankSearchOutcome(items, rerankResponse.rerankerName(), rerankResponse.latencyMs());
    }

    private List<DocumentSearchResultResponse> search(
            DocumentEmbeddingService documentEmbeddingService,
            String query,
            int topK,
            Long documentId
    ) {
        DocumentSearchRequest searchRequest = new DocumentSearchRequest();
        searchRequest.setQuery(query);
        searchRequest.setTopK(topK);
        searchRequest.setDocumentId(documentId);
        return documentEmbeddingService.search(searchRequest);
    }

    private List<RagRetrievedItem> toRetrievedItems(List<DocumentSearchResultResponse> results, boolean rerank) {
        List<RagRetrievedItem> items = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            DocumentSearchResultResponse result = results.get(i);
            items.add(new RagRetrievedItem(
                    i + 1,
                    result.getDocumentId(),
                    result.getHeadingPath(),
                    result.getChunkId(),
                    result.getScore(),
                    contentSnippet(result.getContent()),
                    rerank ? i + 1 : null,
                    rerank ? i + 1 : null,
                    rerank ? result.getScore() : null,
                    rerank ? result.getScore() : null
            ));
        }
        return items;
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

    public record RerankSearchOutcome(
            List<RagRetrievedItem> items,
            String rerankerName,
            long latencyMs
    ) {
    }
}
