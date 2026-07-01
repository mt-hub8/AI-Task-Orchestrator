package com.tuoman.ai_task_orchestrator.vectorstore;

import com.tuoman.ai_task_orchestrator.embedding.EmbeddingVectorUtils;
import com.tuoman.ai_task_orchestrator.entity.DocumentChunkEmbeddingEntity;
import com.tuoman.ai_task_orchestrator.entity.DocumentChunkEntity;
import com.tuoman.ai_task_orchestrator.repository.DocumentChunkEmbeddingRepository;
import com.tuoman.ai_task_orchestrator.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
public class ExactCosineVectorStore implements VectorStore {

    public static final String PROVIDER = "exact";

    private final DocumentChunkEmbeddingRepository documentChunkEmbeddingRepository;

    private final DocumentChunkRepository documentChunkRepository;

    @Override
    public void upsert(List<VectorStoreDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        List<DocumentChunkEmbeddingEntity> entities = documents.stream()
                .filter(Objects::nonNull)
                .map(this::toEntity)
                .toList();

        if (!entities.isEmpty()) {
            documentChunkEmbeddingRepository.saveAll(entities);
        }
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        validateSearchRequest(request);

        VectorSearchFilter filter = request.filter() == null ? VectorSearchFilter.empty() : request.filter();
        List<DocumentChunkEmbeddingEntity> candidates = loadCandidates(request, filter);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, DocumentChunkEntity> chunksById = documentChunkRepository.findAllById(
                        candidates.stream()
                                .map(DocumentChunkEmbeddingEntity::getDocumentChunkId)
                                .filter(Objects::nonNull)
                                .toList()
                )
                .stream()
                .collect(Collectors.toMap(DocumentChunkEntity::getId, Function.identity()));

        List<ScoredVector> scored = candidates.stream()
                .filter(candidate -> matchesDimension(candidate, request.dimension()))
                .map(candidate -> toScoredVector(candidate, chunksById.get(candidate.getDocumentChunkId()), request))
                .filter(Objects::nonNull)
                .filter(result -> matchesMetadata(result.metadata(), filter.metadataEquals()))
                .sorted(Comparator.comparingDouble(ScoredVector::score).reversed())
                .limit(request.topK())
                .toList();

        return IntStream.range(0, scored.size())
                .mapToObj(index -> toSearchResult(scored.get(index), index + 1))
                .toList();
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }
        documentChunkEmbeddingRepository.deleteByDocumentId(documentId);
    }

    @Override
    public void deleteByDocumentIdAndProviderAndModel(Long documentId, String provider, String model) {
        if (documentId == null || isBlank(provider) || isBlank(model)) {
            return;
        }
        documentChunkEmbeddingRepository.deleteByDocumentIdAndEmbeddingProviderAndEmbeddingModel(
                documentId,
                provider,
                model
        );
    }

    private DocumentChunkEmbeddingEntity toEntity(VectorStoreDocument document) {
        validateDocument(document);

        DocumentChunkEmbeddingEntity entity = new DocumentChunkEmbeddingEntity();
        entity.setDocumentId(document.documentId());
        entity.setDocumentChunkId(document.chunkId());
        entity.setEmbeddingProvider(document.provider());
        entity.setEmbeddingModel(document.model());
        entity.setVectorDimension(document.dimension());
        entity.setDistanceMetric(document.distanceMetric());
        entity.setEmbeddingVector(EmbeddingVectorUtils.serialize(document.embedding()));
        return entity;
    }

    private void validateDocument(VectorStoreDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("vector store document must not be null");
        }
        if (document.documentId() == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (document.chunkId() == null) {
            throw new IllegalArgumentException("chunkId must not be null");
        }
        if (isBlank(document.provider())) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (isBlank(document.model())) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (document.dimension() == null || document.dimension() <= 0) {
            throw new IllegalArgumentException("dimension must be greater than 0");
        }
        if (document.embedding() == null || document.embedding().isEmpty()) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        if (document.embedding().size() != document.dimension()) {
            throw new IllegalArgumentException("embedding dimension must match configured dimension");
        }
    }

    private void validateSearchRequest(VectorSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("vector search request must not be null");
        }
        if (request.queryEmbedding() == null || request.queryEmbedding().isEmpty()) {
            throw new IllegalArgumentException("queryEmbedding must not be empty");
        }
        if (request.topK() == null || request.topK() <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }
        if (isBlank(request.provider())) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (isBlank(request.model())) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (request.dimension() == null || request.dimension() <= 0) {
            throw new IllegalArgumentException("dimension must be greater than 0");
        }
        if (request.queryEmbedding().size() != request.dimension()) {
            throw new IllegalArgumentException("queryEmbedding dimension must match request dimension");
        }
    }

    private List<DocumentChunkEmbeddingEntity> loadCandidates(
            VectorSearchRequest request,
            VectorSearchFilter filter
    ) {
        List<Long> documentIds = filter.documentIds() == null ? List.of() : filter.documentIds();
        if (documentIds.isEmpty()) {
            return documentChunkEmbeddingRepository.findByEmbeddingProviderAndEmbeddingModel(
                    request.provider(),
                    request.model()
            );
        }

        return documentIds.stream()
                .filter(Objects::nonNull)
                .flatMap(documentId -> documentChunkEmbeddingRepository.findByDocumentIdAndEmbeddingProviderAndEmbeddingModel(
                        documentId,
                        request.provider(),
                        request.model()
                ).stream())
                .toList();
    }

    private boolean matchesDimension(DocumentChunkEmbeddingEntity candidate, Integer dimension) {
        return candidate.getVectorDimension() != null && candidate.getVectorDimension().equals(dimension);
    }

    private ScoredVector toScoredVector(
            DocumentChunkEmbeddingEntity candidate,
            DocumentChunkEntity chunk,
            VectorSearchRequest request
    ) {
        if (chunk == null) {
            return null;
        }

        List<Double> vector = EmbeddingVectorUtils.deserialize(candidate.getEmbeddingVector());
        if (vector.isEmpty() || vector.size() != request.dimension()) {
            return null;
        }

        double score = EmbeddingVectorUtils.cosineSimilarity(request.queryEmbedding(), vector);
        return new ScoredVector(candidate, chunk, score, metadata(candidate, chunk));
    }

    private boolean matchesMetadata(
            Map<String, String> metadata,
            Map<String, String> metadataEquals
    ) {
        if (metadataEquals == null || metadataEquals.isEmpty()) {
            return true;
        }
        return metadataEquals.entrySet().stream()
                .allMatch(entry -> Objects.equals(metadata.get(entry.getKey()), entry.getValue()));
    }

    private Map<String, String> metadata(
            DocumentChunkEmbeddingEntity embedding,
            DocumentChunkEntity chunk
    ) {
        Map<String, String> metadata = new LinkedHashMap<>();
        put(metadata, "documentId", embedding.getDocumentId());
        put(metadata, "chunkId", chunk.getId());
        put(metadata, "chunkIndex", chunk.getChunkIndex());
        put(metadata, "contentLength", chunk.getContentLength());
        put(metadata, "headingPath", chunk.getHeadingPath());
        put(metadata, "startOffset", chunk.getStartOffset());
        put(metadata, "endOffset", chunk.getEndOffset());
        put(metadata, "chunkStrategy", chunk.getChunkStrategy());
        return metadata;
    }

    private void put(Map<String, String> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, String.valueOf(value));
        }
    }

    private VectorSearchResult toSearchResult(ScoredVector scoredVector, int rank) {
        DocumentChunkEmbeddingEntity embedding = scoredVector.embedding();
        DocumentChunkEntity chunk = scoredVector.chunk();

        return new VectorSearchResult(
                chunk.getId(),
                embedding.getDocumentId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getContentLength(),
                chunk.getHeadingPath(),
                chunk.getStartOffset(),
                chunk.getEndOffset(),
                chunk.getChunkStrategy(),
                scoredVector.score(),
                rank,
                embedding.getEmbeddingProvider(),
                embedding.getEmbeddingModel(),
                embedding.getVectorDimension(),
                embedding.getDistanceMetric(),
                scoredVector.metadata()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ScoredVector(
            DocumentChunkEmbeddingEntity embedding,
            DocumentChunkEntity chunk,
            double score,
            Map<String, String> metadata
    ) {
    }
}
