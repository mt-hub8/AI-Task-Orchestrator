package com.tuoman.ai_task_orchestrator.evaluation;

import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchRequest;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorSearchResult;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorStore;
import com.tuoman.ai_task_orchestrator.vectorstore.VectorStoreDocument;

import java.util.List;

public class LatencyMeasuringVectorStore implements VectorStore {

    private final VectorStore delegate;

    private long totalSearchLatencyNanos;

    private int searchCount;

    public LatencyMeasuringVectorStore(VectorStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public void upsert(List<VectorStoreDocument> documents) {
        delegate.upsert(documents);
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        long start = System.nanoTime();
        try {
            return delegate.search(request);
        } finally {
            totalSearchLatencyNanos += System.nanoTime() - start;
            searchCount++;
        }
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        delegate.deleteByDocumentId(documentId);
    }

    @Override
    public void deleteByDocumentIdAndProviderAndModel(Long documentId, String provider, String model) {
        delegate.deleteByDocumentIdAndProviderAndModel(documentId, provider, model);
    }

    public long getTotalSearchLatencyNanos() {
        return totalSearchLatencyNanos;
    }

    public int getSearchCount() {
        return searchCount;
    }
}
