package com.tuoman.ai_task_orchestrator.vectorstore;

import java.util.List;
import java.util.Map;

public record VectorStoreDocument(
        Long chunkId,
        Long documentId,
        String content,
        List<Double> embedding,
        String provider,
        String model,
        Integer dimension,
        String distanceMetric,
        Map<String, String> metadata
) {
}
