package com.tuoman.ai_task_orchestrator.service;

import com.tuoman.ai_task_orchestrator.common.error.BusinessException;
import com.tuoman.ai_task_orchestrator.dto.RagAnswerRequest;
import com.tuoman.ai_task_orchestrator.dto.RagAnswerResponse;
import com.tuoman.ai_task_orchestrator.dto.RagCitationResponse;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingProvider;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingProviderException;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingRequest;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingResponse;
import com.tuoman.ai_task_orchestrator.embedding.MockEmbeddingClient;
import com.tuoman.ai_task_orchestrator.llm.LlmClient;
import com.tuoman.ai_task_orchestrator.llm.LlmRequest;
import com.tuoman.ai_task_orchestrator.llm.LlmResponse;
import com.tuoman.ai_task_orchestrator.vectorstore.ExactCosineVectorStore;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchFilter;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchRequest;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchResult;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorStore;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorStoreProperties;
import com.tuoman.ai_task_orchestrator.vectorstore.qdrant.QdrantVectorStoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagAnswerServiceTest {

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
    void answerShouldEmbedQuerySearchBuildPromptCallLlmAndReturnMetadata() {
        RagAnswerRequest request = request("Why use outbox?", 3);
        List<VectorSearchResult> searchResults = List.of(
                searchResult(1L, 10L, 0, 0.9, "Outbox avoids dual write loss."),
                searchResult(1L, 11L, 1, 0.8, "Atomic claim prevents duplicate execution.")
        );
        when(embeddingProvider.embed(any(EmbeddingRequest.class))).thenReturn(embeddingResponse());
        when(vectorStore.search(any(VectorSearchRequest.class))).thenReturn(searchResults);
        when(ragPromptBuilder.buildPrompt(any(), any())).thenReturn("rag prompt");
        when(llmClient.generate(any(LlmRequest.class))).thenReturn(successResponse("Answer with [1] and [2]."));

        RagAnswerResponse response = ragAnswerService.answer(request);

        assertThat(response.getAnswer()).isEqualTo("Answer with [1] and [2].");
        assertThat(response.getCitations()).hasSize(2);
        assertThat(response.getCitations().get(0).getSourceIndex()).isEqualTo(1);
        assertThat(response.getCitations().get(0).getChunkId()).isEqualTo(10L);
        assertThat(response.getCitations().get(1).getSourceIndex()).isEqualTo(2);
        assertThat(response.getRetrieval().getTopK()).isEqualTo(3);
        assertThat(response.getRetrieval().getReturned()).isEqualTo(2);
        assertThat(response.getRetrieval().getProvider()).isEqualTo("mock");
        assertThat(response.getRetrieval().getModel()).isEqualTo("mock-embedding-v1");
        assertThat(response.getRetrieval().getDimension()).isEqualTo(128);
        assertThat(response.getRetrieval().getVectorStore()).isEqualTo("ExactCosineVectorStore");
        assertThat(response.getGeneration().getProvider()).isEqualTo("mock");
        assertThat(response.getGeneration().getModel()).isEqualTo("mock-llm");
        assertThat(response.getGeneration().getSkipped()).isNull();

        ArgumentCaptor<EmbeddingRequest> embeddingRequestCaptor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingProvider).embed(embeddingRequestCaptor.capture());
        assertThat(embeddingRequestCaptor.getValue().getText()).isEqualTo("Why use outbox?");

        ArgumentCaptor<VectorSearchRequest> searchRequestCaptor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        verify(vectorStore).search(searchRequestCaptor.capture());
        assertThat(searchRequestCaptor.getValue().topK()).isEqualTo(3);
        assertThat(searchRequestCaptor.getValue().filter()).isEqualTo(VectorSearchFilter.empty());

        verify(ragPromptBuilder).buildPrompt(request.getQuery(), response.getCitations());
        verify(llmClient).generate(any(LlmRequest.class));
    }

    @Test
    void answerShouldUseDefaultTopKWhenMissing() {
        when(embeddingProvider.embed(any(EmbeddingRequest.class))).thenReturn(embeddingResponse());
        when(vectorStore.search(any(VectorSearchRequest.class))).thenReturn(List.of());

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
        when(embeddingProvider.embed(any(EmbeddingRequest.class))).thenReturn(embeddingResponse());
        when(vectorStore.search(any(VectorSearchRequest.class))).thenReturn(List.of());

        RagAnswerResponse response = ragAnswerService.answer(request("No context question", 5));

        assertThat(response.getAnswer()).isEqualTo("根据当前检索到的文档内容，无法确定。");
        assertThat(response.getCitations()).isEmpty();
        assertThat(response.getRetrieval().getReturned()).isZero();
        assertThat(response.getGeneration().getSkipped()).isTrue();
        assertThat(response.getGeneration().getReason()).isEqualTo("NO_RETRIEVED_CONTEXT");
        verify(ragPromptBuilder, never()).buildPrompt(any(), any());
        verify(llmClient, never()).generate(any());
    }

    @Test
    void answerShouldLimitCitationSnippetLength() {
        String longContent = "a".repeat(500);
        when(embeddingProvider.embed(any(EmbeddingRequest.class))).thenReturn(embeddingResponse());
        when(vectorStore.search(any(VectorSearchRequest.class))).thenReturn(List.of(
                searchResult(1L, 10L, 0, 0.9, longContent)
        ));
        when(ragPromptBuilder.buildPrompt(any(), any())).thenReturn("prompt");
        when(llmClient.generate(any())).thenReturn(successResponse("answer"));

        RagAnswerResponse response = ragAnswerService.answer(request("query", 1));

        assertThat(response.getCitations().getFirst().getContentSnippet()).hasSize(400);
    }

    @Test
    void answerShouldPropagateEmbeddingProviderFailure() {
        when(embeddingProvider.embed(any(EmbeddingRequest.class)))
                .thenThrow(new EmbeddingProviderException("embedding failed"));

        assertThatThrownBy(() -> ragAnswerService.answer(request("query", 3)))
                .isInstanceOf(EmbeddingProviderException.class);
        verify(vectorStore, never()).search(any());
    }

    @Test
    void answerShouldConvertVectorStoreFailureToBusinessException() {
        when(embeddingProvider.embed(any(EmbeddingRequest.class))).thenReturn(embeddingResponse());
        when(vectorStore.search(any(VectorSearchRequest.class)))
                .thenThrow(new IllegalStateException("vector search failed"));

        assertThatThrownBy(() -> ragAnswerService.answer(request("query", 3)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.tuoman.ai_task_orchestrator.common.error.ErrorCode.VECTOR_STORE_ERROR);
    }

    @Test
    void answerShouldPropagateQdrantVectorStoreFailure() {
        when(embeddingProvider.embed(any(EmbeddingRequest.class))).thenReturn(embeddingResponse());
        when(vectorStore.search(any(VectorSearchRequest.class)))
                .thenThrow(new QdrantVectorStoreException("qdrant failed"));

        assertThatThrownBy(() -> ragAnswerService.answer(request("query", 3)))
                .isInstanceOf(QdrantVectorStoreException.class);
    }

    @Test
    void answerShouldConvertLlmFailureToBusinessException() {
        when(embeddingProvider.embed(any(EmbeddingRequest.class))).thenReturn(embeddingResponse());
        when(vectorStore.search(any(VectorSearchRequest.class))).thenReturn(List.of(
                searchResult(1L, 10L, 0, 0.9, "content")
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

    private EmbeddingResponse embeddingResponse() {
        return new EmbeddingResponse(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                MockEmbeddingClient.DIMENSION,
                MockEmbeddingClient.DISTANCE_METRIC,
                List.of(0.1, 0.2)
        );
    }

    private VectorSearchResult searchResult(
            Long documentId,
            Long chunkId,
            Integer chunkIndex,
            Double score,
            String content
    ) {
        return new VectorSearchResult(
                chunkId,
                documentId,
                chunkIndex,
                content,
                content.length(),
                "Heading",
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

    private LlmResponse successResponse(String content) {
        LlmResponse response = new LlmResponse();
        response.setProvider("mock");
        response.setModel("mock-llm");
        response.setContent(content);
        response.setSuccess(true);
        return response;
    }
}
