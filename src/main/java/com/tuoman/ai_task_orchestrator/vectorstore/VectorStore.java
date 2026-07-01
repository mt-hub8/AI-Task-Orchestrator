package com.tuoman.ai_task_orchestrator.vectorstore;

import java.util.List;

public interface VectorStore {

    void upsert(List<VectorStoreDocument> documents);

    List<VectorSearchResult> search(VectorSearchRequest request);

    void deleteByDocumentId(Long documentId);

    void deleteByDocumentIdAndProviderAndModel(Long documentId, String provider, String model);
}
