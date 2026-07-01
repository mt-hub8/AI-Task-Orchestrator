package com.tuoman.ai_task_orchestrator.service;

import com.tuoman.ai_task_orchestrator.dto.DocumentEmbeddingResponse;
import com.tuoman.ai_task_orchestrator.dto.DocumentSearchRequest;
import com.tuoman.ai_task_orchestrator.dto.DocumentSearchResultResponse;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingRequest;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingResponse;
import com.tuoman.ai_task_orchestrator.embedding.EmbeddingProvider;
import com.tuoman.ai_task_orchestrator.entity.DocumentChunkEntity;
import com.tuoman.ai_task_orchestrator.repository.DocumentChunkRepository;
import com.tuoman.ai_task_orchestrator.repository.DocumentRepository;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchFilter;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchRequest;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchResult;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorStore;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorStoreDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentEmbeddingService {

    private static final int DEFAULT_TOP_K = 5;

    private static final int MAX_TOP_K = 20;

    private final DocumentRepository documentRepository;

    private final DocumentChunkRepository documentChunkRepository;

    private final EmbeddingProvider embeddingProvider;

    private final VectorStore vectorStore;

    @Transactional
    public DocumentEmbeddingResponse embedDocument(Long documentId) {
        ensureDocumentExists(documentId);

        String embeddingProviderName = embeddingProvider.provider();
        String embeddingModel = embeddingProvider.model();

        List<DocumentChunkEntity> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        if (chunks.isEmpty()) {
            return new DocumentEmbeddingResponse(
                    documentId,
                    embeddingProviderName,
                    embeddingModel,
                    embeddingProvider.dimension(),
                    "COSINE",
                    0
            );
        }

        vectorStore.deleteByDocumentIdAndProviderAndModel(
                documentId,
                embeddingProviderName,
                embeddingModel
        );

        List<VectorStoreDocument> embeddings = chunks.stream()
                .map(chunk -> toVectorStoreDocument(documentId, chunk, embeddingModel))
                .toList();

        vectorStore.upsert(embeddings);

        return new DocumentEmbeddingResponse(
                documentId,
                embeddingProviderName,
                embeddingModel,
                embeddingProvider.dimension(),
                "COSINE",
                embeddings.size()
        );
    }

    @Transactional(readOnly = true)
    public List<DocumentSearchResultResponse> search(DocumentSearchRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }

        int topK = normalizeTopK(request.getTopK());
        String embeddingProviderName = normalizeProvider(request.getEmbeddingProvider());
        String embeddingModel = normalizeModel(request.getEmbeddingModel());

        if (request.getDocumentId() != null) {
            ensureDocumentExists(request.getDocumentId());
        }

        EmbeddingRequest embeddingRequest = new EmbeddingRequest();
        embeddingRequest.setText(request.getQuery());
        embeddingRequest.setModel(embeddingModel);
        EmbeddingResponse queryEmbedding = embeddingProvider.embed(embeddingRequest);

        VectorSearchFilter filter = request.getDocumentId() == null
                ? VectorSearchFilter.empty()
                : new VectorSearchFilter(List.of(request.getDocumentId()), Map.of());

        return vectorStore.search(new VectorSearchRequest(
                        queryEmbedding.getVector(),
                        topK,
                        embeddingProviderName,
                        embeddingModel,
                        queryEmbedding.getDimension(),
                        filter
                ))
                .stream()
                .map(this::toSearchResponse)
                .toList();
    }

    private VectorStoreDocument toVectorStoreDocument(
            Long documentId,
            DocumentChunkEntity chunk,
            String embeddingModel
    ) {
        EmbeddingRequest request = new EmbeddingRequest();
        request.setText(chunk.getContent());
        request.setModel(embeddingModel);
        EmbeddingResponse response = embeddingProvider.embed(request);

        return new VectorStoreDocument(
                chunk.getId(),
                documentId,
                chunk.getContent(),
                response.getVector(),
                response.getProvider(),
                response.getModel(),
                response.getDimension(),
                response.getDistanceMetric(),
                chunkMetadata(chunk)
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

    private Map<String, String> chunkMetadata(DocumentChunkEntity chunk) {
        return Map.of(
                "chunkStrategy", chunk.getChunkStrategy() == null ? "" : chunk.getChunkStrategy(),
                "headingPath", chunk.getHeadingPath() == null ? "" : chunk.getHeadingPath()
        );
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }

        if (topK <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topK must be greater than 0");
        }

        return Math.min(topK, MAX_TOP_K);
    }

    private String normalizeProvider(String embeddingProvider) {
        return embeddingProvider == null || embeddingProvider.isBlank()
                ? this.embeddingProvider.provider()
                : embeddingProvider;
    }

    private String normalizeModel(String embeddingModel) {
        return embeddingModel == null || embeddingModel.isBlank()
                ? embeddingProvider.model()
                : embeddingModel;
    }

    private void ensureDocumentExists(Long documentId) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }
}
