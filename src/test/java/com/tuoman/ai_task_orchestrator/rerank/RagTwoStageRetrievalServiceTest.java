package com.tuoman.ai_task_orchestrator.rerank;

import com.tuoman.ai_task_orchestrator.common.error.BusinessException;
import com.tuoman.ai_task_orchestrator.dto.DocumentSearchResultResponse;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingProvider;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingRequest;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingResponse;
import com.tuoman.ai_task_orchestrator.embedding.MockEmbeddingClient;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchRequest;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchResult;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagTwoStageRetrievalServiceTest {

    @Mock
    private EmbeddingProvider embeddingProvider;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private Reranker reranker;

    private RagRerankProperties rerankProperties;

    private RagTwoStageRetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        rerankProperties = new RagRerankProperties();
        retrievalService = new RagTwoStageRetrievalService(
                embeddingProvider,
                vectorStore,
                reranker,
                rerankProperties
        );
        lenient().when(embeddingProvider.provider()).thenReturn(MockEmbeddingClient.PROVIDER);
        lenient().when(embeddingProvider.model()).thenReturn(MockEmbeddingClient.DEFAULT_MODEL);
        lenient().when(embeddingProvider.dimension()).thenReturn(MockEmbeddingClient.DIMENSION);
        lenient().when(embeddingProvider.embed(any(EmbeddingRequest.class))).thenReturn(new EmbeddingResponse(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                MockEmbeddingClient.DIMENSION,
                MockEmbeddingClient.DISTANCE_METRIC,
                List.of(0.1, 0.2)
        ));
    }

    @Test
    void retrieveShouldKeepOriginalOrderWhenRerankDisabled() {
        when(vectorStore.search(any(VectorSearchRequest.class))).thenReturn(List.of(
                searchResult(10L, 0.9, "low overlap"),
                searchResult(11L, 0.8, "cache key tuple")
        ));

        RagTwoStageRetrievalService.RagRetrievalOutcome outcome = retrievalService.retrieve("cache key", 2, false);

        assertThat(outcome.rerankEnabled()).isFalse();
        assertThat(outcome.chunks()).extracting(RagTwoStageRetrievalService.RagRetrievedChunk::chunkId)
                .containsExactly(10L, 11L);
        assertThat(outcome.candidateTopK()).isEqualTo(2);
    }

    @Test
    void retrieveShouldUseCandidateTopKAndRerankWhenEnabled() {
        rerankProperties.setEnabled(true);
        rerankProperties.setCandidateTopK(3);
        when(vectorStore.search(any(VectorSearchRequest.class))).thenReturn(List.of(
                searchResult(10L, 0.99, "irrelevant"),
                searchResult(11L, 0.50, "cache key"),
                searchResult(12L, 0.40, "other")
        ));
        when(reranker.rerank(any())).thenReturn(new RerankResponse(
                List.of(new RerankedItem(1, 2, 1L, "heading", 11L, "cache key", 0.50, 0.95)),
                "lexical",
                2L
        ));

        RagTwoStageRetrievalService.RagRetrievalOutcome outcome = retrievalService.retrieve("cache key", 1, true);

        assertThat(outcome.rerankEnabled()).isTrue();
        assertThat(outcome.candidateTopK()).isEqualTo(3);
        assertThat(outcome.chunks()).hasSize(1);
        assertThat(outcome.chunks().getFirst().chunkId()).isEqualTo(11L);
        assertThat(outcome.chunks().getFirst().originalRank()).isEqualTo(2);
    }

    @Test
    void resolveCandidateTopKShouldRejectInvalidConfiguration() {
        rerankProperties.setCandidateTopK(2);

        assertThatThrownBy(() -> retrievalService.resolveCandidateTopK(5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("candidateTopK must be greater than or equal to finalTopK");
    }

    private VectorSearchResult searchResult(Long chunkId, double score, String content) {
        return new VectorSearchResult(
                chunkId,
                1L,
                0,
                content,
                content.length(),
                "heading",
                0,
                content.length(),
                "TEST",
                score,
                1,
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                MockEmbeddingClient.DIMENSION,
                MockEmbeddingClient.DISTANCE_METRIC,
                Map.of()
        );
    }
}
