package com.tuoman.ai_task_orchestrator.service;

import com.tuoman.ai_task_orchestrator.common.error.BusinessException;
import com.tuoman.ai_task_orchestrator.dto.DocumentSearchResultResponse;
import com.tuoman.ai_task_orchestrator.dto.RagAnswerRequest;
import com.tuoman.ai_task_orchestrator.dto.RagAnswerResponse;
import com.tuoman.ai_task_orchestrator.dto.RagCitationResponse;
import com.tuoman.ai_task_orchestrator.dto.RagGenerationMetadataResponse;
import com.tuoman.ai_task_orchestrator.dto.RagRetrievalMetadataResponse;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingProvider;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingRequest;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingResponse;
import com.tuoman.ai_task_orchestrator.llm.LlmClient;
import com.tuoman.ai_task_orchestrator.llm.LlmRequest;
import com.tuoman.ai_task_orchestrator.llm.LlmResponse;
import com.tuoman.ai_task_orchestrator.vectorstore.ExactCosineVectorStore;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchFilter;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchRequest;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchResult;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorStore;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorStoreProperties;
import com.tuoman.ai_task_orchestrator.vectorstore.qdrant.QdrantVectorStore;
import com.tuoman.ai_task_orchestrator.vectorstore.qdrant.QdrantVectorStoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class RagAnswerService {

    private static final int DEFAULT_TOP_K = 5;

    private static final int MAX_TOP_K = 10;

    private static final int CONTENT_SNIPPET_MAX_LENGTH = 400;

    private static final String NO_CONTEXT_ANSWER = "根据当前检索到的文档内容，无法确定。";

    private static final String NO_CONTEXT_REASON = "NO_RETRIEVED_CONTEXT";

    private static final String DEFAULT_LLM_MODEL = "mock-llm";

    private final EmbeddingProvider embeddingProvider;

    private final VectorStore vectorStore;

    private final VectorStoreProperties vectorStoreProperties;

    private final RagPromptBuilder ragPromptBuilder;

    private final LlmClient llmClient;

    public RagAnswerResponse answer(RagAnswerRequest request) {
        if (request == null) {
            throw BusinessException.validationError("request must not be null");
        }

        int topK = normalizeTopK(request.getTopK());
        List<DocumentSearchResultResponse> chunks = retrieveChunks(request.getQuery(), topK);
        List<RagCitationResponse> citations = toCitations(chunks);
        RagRetrievalMetadataResponse retrieval = toRetrievalMetadata(topK, chunks);

        if (citations.isEmpty()) {
            return new RagAnswerResponse(
                    NO_CONTEXT_ANSWER,
                    List.of(),
                    retrieval,
                    new RagGenerationMetadataResponse(null, null, true, NO_CONTEXT_REASON)
            );
        }

        String prompt = ragPromptBuilder.buildPrompt(request.getQuery(), citations);

        LlmRequest llmRequest = new LlmRequest();
        llmRequest.setPrompt(prompt);
        llmRequest.setModel(DEFAULT_LLM_MODEL);

        LlmResponse llmResponse = llmClient.generate(llmRequest);
        if (llmResponse == null || !llmResponse.isSuccess() || llmResponse.getContent() == null || llmResponse.getContent().isBlank()) {
            String message = llmResponse == null || llmResponse.getErrorMessage() == null
                    ? "LLM provider error"
                    : llmResponse.getErrorMessage();
            throw BusinessException.llmProviderError(message);
        }

        return new RagAnswerResponse(
                llmResponse.getContent(),
                citations,
                retrieval,
                new RagGenerationMetadataResponse(
                        llmResponse.getProvider(),
                        llmResponse.getModel(),
                        null,
                        null
                )
        );
    }

    private List<DocumentSearchResultResponse> retrieveChunks(String query, int topK) {
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

    private List<RagCitationResponse> toCitations(List<DocumentSearchResultResponse> chunks) {
        return IntStream.range(0, chunks.size())
                .mapToObj(index -> toCitation(index + 1, chunks.get(index)))
                .toList();
    }

    private RagCitationResponse toCitation(int sourceIndex, DocumentSearchResultResponse chunk) {
        return new RagCitationResponse(
                sourceIndex,
                chunk.getDocumentId(),
                chunk.getChunkId(),
                chunk.getScore(),
                contentSnippet(chunk.getContent())
        );
    }

    private RagRetrievalMetadataResponse toRetrievalMetadata(int topK, List<DocumentSearchResultResponse> chunks) {
        return new RagRetrievalMetadataResponse(
                topK,
                chunks.size(),
                embeddingProvider.provider(),
                embeddingProvider.model(),
                embeddingProvider.dimension(),
                resolveVectorStoreName()
        );
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

    private String resolveVectorStoreName() {
        String provider = vectorStoreProperties.getProvider();
        if (ExactCosineVectorStore.PROVIDER.equalsIgnoreCase(provider)) {
            return "ExactCosineVectorStore";
        }
        if (QdrantVectorStore.PROVIDER.equalsIgnoreCase(provider)) {
            return "QdrantVectorStore";
        }
        return vectorStore.getClass().getSimpleName();
    }

    private String contentSnippet(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= CONTENT_SNIPPET_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, CONTENT_SNIPPET_MAX_LENGTH);
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        if (topK < 1) {
            throw BusinessException.validationError("topK must be greater than or equal to 1");
        }
        if (topK > MAX_TOP_K) {
            throw BusinessException.validationError("topK must be less than or equal to 10");
        }
        return topK;
    }
}
