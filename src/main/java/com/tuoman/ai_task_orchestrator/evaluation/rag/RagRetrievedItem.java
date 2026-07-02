package com.tuoman.ai_task_orchestrator.evaluation.rag;

public record RagRetrievedItem(
        int rank,
        Long documentId,
        String documentTitle,
        Long chunkId,
        Double score,
        String contentSnippet
) {
}
