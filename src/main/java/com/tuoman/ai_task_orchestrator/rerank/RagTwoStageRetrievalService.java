package com.tuoman.ai_task_orchestrator.rerank;

import com.tuoman.ai_task_orchestrator.common.error.BusinessException;
import com.tuoman.ai_task_orchestrator.dto.DocumentSearchResultResponse;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingProvider;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingRequest;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingResponse;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchFilter;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchRequest;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchResult;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorStore;
import com.tuoman.ai_task_orchestrator.vectorstore.qdrant.QdrantVectorStoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RagTwoStageRetrievalService {

    private final EmbeddingProvider embeddingProvider;

    private final VectorStore vectorStore;

    private final Reranker reranker;

    private final RagRerankProperties rerankProperties;

    public RagRetrievalOutcome retrieve(String query, int finalTopK) {
        return retrieve(query, finalTopK, rerankProperties.isEnabled());
    }

    public RagRetrievalOutcome retrieve(String query, int finalTopK, boolean rerankEnabled) {
        int searchTopK = rerankEnabled ? resolveCandidateTopK(finalTopK) : finalTopK;
        List<DocumentSearchResultResponse> vectorResults = search(query, searchTopK);

        if (!rerankEnabled) {
            List<RagRetrievedChunk> chunks = new ArrayList<>();
            for (int i = 0; i < vectorResults.size(); i++) {
                DocumentSearchResultResponse result = vectorResults.get(i);
                chunks.add(new RagRetrievedChunk(
                        i + 1,
                        i + 1,
                        result.getDocumentId(),
                        result.getHeadingPath(),
                        result.getChunkId(),
                        result.getScore(),
                        null,
                        result.getContent()
                ));
            }
            return new RagRetrievalOutcome(
                    chunks,
                    finalTopK,
                    finalTopK,
                    false,
                    null,
                    0L
            );
        }

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
        List<RagRetrievedChunk> chunks = rerankResponse.items().stream()
                .map(item -> new RagRetrievedChunk(
                        item.rerankedRank(),
                        item.originalRank(),
                        item.documentId(),
                        item.documentTitle(),
                        item.chunkId(),
                        item.originalScore(),
                        item.rerankScore(),
                        item.content()
                ))
                .toList();

        return new RagRetrievalOutcome(
                chunks,
                finalTopK,
                searchTopK,
                true,
                rerankResponse.rerankerName(),
                rerankResponse.latencyMs()
        );
    }

    public int resolveCandidateTopK(int finalTopK) {
        int candidateTopK = rerankProperties.getCandidateTopK();
        if (candidateTopK < finalTopK) {
            throw BusinessException.validationError("candidateTopK must be greater than or equal to finalTopK");
        }
        return candidateTopK;
    }

    private List<DocumentSearchResultResponse> search(String query, int topK) {
        EmbeddingRequest embeddingRequest = new EmbeddingRequest();
        embeddingRequest.setText(query);
        embeddingRequest.setModel(embeddingProvider.model());
        EmbeddingResponse queryEmbedding = embeddingProvider.embed(embeddingRequest);

        try {
            return vectorStore.search(new VectorSearchRequest(
                            queryEmbedding.getVector(),
                            topK,
                            embeddingProvider.provider(),
                            embeddingProvider.model(),
                            queryEmbedding.getDimension(),
                            VectorSearchFilter.empty()
                    ))
                    .stream()
                    .map(this::toSearchResponse)
                    .toList();
        } catch (QdrantVectorStoreException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw BusinessException.vectorStoreError(
                    exception.getMessage() == null ? "Vector store search failed" : exception.getMessage()
            );
        }
    }

    private DocumentSearchResultResponse toSearchResponse(VectorSearchResult result) {
        return new DocumentSearchResultResponse(
                result.documentId(),
                result.chunkId(),
                result.chunkIndex(),
                result.score(),
                result.content(),
                result.contentLength(),
                result.headingPath(),
                result.startOffset(),
                result.endOffset(),
                result.chunkStrategy(),
                result.provider(),
                result.model(),
                result.distanceMetric()
        );
    }

    public record RagRetrievedChunk(
            int rerankedRank,
            int originalRank,
            Long documentId,
            String documentTitle,
            Long chunkId,
            Double originalScore,
            Double rerankScore,
            String content
    ) {
    }

    public record RagRetrievalOutcome(
            List<RagRetrievedChunk> chunks,
            int finalTopK,
            int candidateTopK,
            boolean rerankEnabled,
            String rerankerName,
            long rerankLatencyMs
    ) {
    }
}
