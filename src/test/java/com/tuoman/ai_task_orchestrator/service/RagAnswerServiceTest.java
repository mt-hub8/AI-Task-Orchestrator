package com.tuoman.ai_task_orchestrator.service;

import com.tuoman.ai_task_orchestrator.common.error.BusinessException;
import com.tuoman.ai_task_orchestrator.dto.RagAnswerRequest;
import com.tuoman.ai_task_orchestrator.dto.RagAnswerResponse;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingProvider;
import com.tuoman.ai_task_orchestrator.embedding.MockEmbeddingClient;
import com.tuoman.ai_task_orchestrator.llm.LlmClient;
import com.tuoman.ai_task_orchestrator.llm.LlmRequest;
import com.tuoman.ai_task_orchestrator.llm.LlmResponse;
import com.tuoman.ai_task_orchestrator.rerank.RagTwoStageRetrievalService;
import com.tuoman.ai_task_orchestrator.rerank.RagTwoStageRetrievalService.RagRetrievalOutcome;
import com.tuoman.ai_task_orchestrator.rerank.RagTwoStageRetrievalService.RagRetrievedChunk;
import com.tuoman.ai_task_orchestrator.vectorstore.ExactCosineVectorStore;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorStore;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorStoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagAnswerServiceTest {

    @Mock
    private RagTwoStageRetrievalService ragTwoStageRetrievalService;

    @Mock
    private EmbeddingProvider embeddingProvider;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private VectorStoreProperties vectorStoreProperties;

    @Mock
    private RagPromptBuilder ragPromptBuilder;

    @Mock
    private LlmClient llmClient;

    @InjectMocks
    private RagAnswerService ragAnswerService;

    @BeforeEach
    void setUpProviderMetadata() {
        lenient().when(embeddingProvider.provider()).thenReturn(MockEmbeddingClient.PROVIDER);
        lenient().when(embeddingProvider.model()).thenReturn(MockEmbeddingClient.DEFAULT_MODEL);
        lenient().when(embeddingProvider.dimension()).thenReturn(MockEmbeddingClient.DIMENSION);
        lenient().when(vectorStoreProperties.getProvider()).thenReturn(ExactCosineVectorStore.PROVIDER);
    }

    @Test
    void answerShouldUseRetrievalOutcomeBuildPromptCallLlmAndReturnMetadata() {
        RagAnswerRequest request = request("Why use outbox?", 3);
        RagRetrievalOutcome outcome = new RagRetrievalOutcome(
                List.of(
                        chunk(1, 1, 10L, 0.9, null, "Outbox avoids dual write loss."),
                        chunk(2, 2, 11L, 0.8, null, "Atomic claim prevents duplicate execution.")
                ),
                3,
                3,
                false,
                null,
                0L
        );
        when(ragTwoStageRetrievalService.retrieve("Why use outbox?", 3)).thenReturn(outcome);
        when(ragPromptBuilder.buildPrompt(any(), any())).thenReturn("rag prompt");
        when(llmClient.generate(any(LlmRequest.class))).thenReturn(successResponse("Answer with [1] and [2]."));

        RagAnswerResponse response = ragAnswerService.answer(request);

        assertThat(response.getAnswer()).isEqualTo("Answer with [1] and [2].");
        assertThat(response.getCitations()).hasSize(2);
        assertThat(response.getCitations().get(0).getSourceIndex()).isEqualTo(1);
        assertThat(response.getCitations().get(0).getChunkId()).isEqualTo(10L);
        assertThat(response.getRetrieval().getTopK()).isEqualTo(3);
        assertThat(response.getRetrieval().getReturned()).isEqualTo(2);
        assertThat(response.getRetrieval().getRerankEnabled()).isFalse();
        assertThat(response.getRetrieval().getRerankerName()).isNull();
        verify(ragPromptBuilder).buildPrompt(request.getQuery(), response.getCitations());
        verify(llmClient).generate(any(LlmRequest.class));
    }

    @Test
    void answerShouldUseRerankedOrderWhenRerankEnabled() {
        RagRetrievalOutcome outcome = new RagRetrievalOutcome(
                List.of(chunk(1, 2, 20L, 0.5, 0.95, "cache key four tuple")),
                1,
                20,
                true,
                "lexical",
                3L
        );
        when(ragTwoStageRetrievalService.retrieve("cache key", 1)).thenReturn(outcome);
        when(ragPromptBuilder.buildPrompt(any(), any())).thenReturn("prompt");
        when(llmClient.generate(any())).thenReturn(successResponse("answer"));

        RagAnswerResponse response = ragAnswerService.answer(request("cache key", 1));

        assertThat(response.getCitations().getFirst().getChunkId()).isEqualTo(20L);
        assertThat(response.getCitations().getFirst().getOriginalRank()).isEqualTo(2);
        assertThat(response.getCitations().getFirst().getRerankedRank()).isEqualTo(1);
        assertThat(response.getCitations().getFirst().getRerankScore()).isEqualTo(0.95);
        assertThat(response.getRetrieval().getRerankEnabled()).isTrue();
        assertThat(response.getRetrieval().getRerankerName()).isEqualTo("lexical");
        assertThat(response.getRetrieval().getCandidateTopK()).isEqualTo(20);
        assertThat(response.getRetrieval().getRerankLatencyMs()).isEqualTo(3L);
    }

    @Test
    void answerShouldUseDefaultTopKWhenMissing() {
        when(ragTwoStageRetrievalService.retrieve(eq("query"), eq(5)))
                .thenReturn(new RagRetrievalOutcome(List.of(), 5, 5, false, null, 0L));

        RagAnswerResponse response = ragAnswerService.answer(request("query", null));

        assertThat(response.getRetrieval().getTopK()).isEqualTo(5);
        verify(llmClient, never()).generate(any());
    }

    @Test
    void answerShouldRejectTopKBelowOne() {
        assertThatThrownBy(() -> ragAnswerService.answer(request("query", 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.tuoman.ai_task_orchestrator.common.error.ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void answerShouldRejectTopKAboveMax() {
        assertThatThrownBy(() -> ragAnswerService.answer(request("query", 11)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.tuoman.ai_task_orchestrator.common.error.ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void answerShouldReturnNoContextResponseWithoutCallingLlm() {
        when(ragTwoStageRetrievalService.retrieve(anyString(), anyInt()))
                .thenReturn(new RagRetrievalOutcome(List.of(), 5, 5, false, null, 0L));

        RagAnswerResponse response = ragAnswerService.answer(request("No context question", 5));

        assertThat(response.getAnswer()).isEqualTo("根据当前检索到的文档内容，无法确定。");
        assertThat(response.getCitations()).isEmpty();
        assertThat(response.getGeneration().getSkipped()).isTrue();
        verify(llmClient, never()).generate(any());
    }

    @Test
    void answerShouldLimitCitationSnippetLength() {
        String longContent = "a".repeat(500);
        when(ragTwoStageRetrievalService.retrieve(anyString(), anyInt())).thenReturn(new RagRetrievalOutcome(
                List.of(chunk(1, 1, 10L, 0.9, null, longContent)),
                1,
                1,
                false,
                null,
                0L
        ));
        when(ragPromptBuilder.buildPrompt(any(), any())).thenReturn("prompt");
        when(llmClient.generate(any())).thenReturn(successResponse("answer"));

        RagAnswerResponse response = ragAnswerService.answer(request("query", 1));

        assertThat(response.getCitations().getFirst().getContentSnippet()).hasSize(400);
    }

    @Test
    void answerShouldConvertLlmFailureToBusinessException() {
        when(ragTwoStageRetrievalService.retrieve(anyString(), anyInt())).thenReturn(new RagRetrievalOutcome(
                List.of(chunk(1, 1, 10L, 0.9, null, "content")),
                1,
                1,
                false,
                null,
                0L
        ));
        when(ragPromptBuilder.buildPrompt(any(), any())).thenReturn("prompt");

        LlmResponse failed = new LlmResponse();
        failed.setSuccess(false);
        failed.setErrorMessage("llm failed");
        when(llmClient.generate(any())).thenReturn(failed);

        assertThatThrownBy(() -> ragAnswerService.answer(request("query", 1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.tuoman.ai_task_orchestrator.common.error.ErrorCode.LLM_PROVIDER_ERROR);
    }

    private RagAnswerRequest request(String query, Integer topK) {
        RagAnswerRequest request = new RagAnswerRequest();
        request.setQuery(query);
        request.setTopK(topK);
        return request;
    }

    private RagRetrievedChunk chunk(
            int rerankedRank,
            int originalRank,
            Long chunkId,
            Double originalScore,
            Double rerankScore,
            String content
    ) {
        return new RagRetrievedChunk(
                rerankedRank,
                originalRank,
                1L,
                "heading",
                chunkId,
                originalScore,
                rerankScore,
                content
        );
    }

    private LlmResponse successResponse(String content) {
        LlmResponse response = new LlmResponse();
        response.setProvider("mock");
        response.setModel("mock-llm");
        response.setContent(content);
        response.setSuccess(true);
        return response;
    }
}
