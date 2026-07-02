package com.tuoman.ai_task_orchestrator.evaluation.rag;

public record RagRetrievedItem(
        int rank,
        Long documentId,
        String documentTitle,
        Long chunkId,
        Double score,
        String contentSnippet,
        Integer originalRank,
        Integer rerankedRank,
        Double originalScore,
        Double rerankScore
) {
    public RagRetrievedItem(
            int rank,
            Long documentId,
            String documentTitle,
            Long chunkId,
            Double score,
            String contentSnippet
    ) {
        this(rank, documentId, documentTitle, chunkId, score, contentSnippet, null, null, null, null);
    }
}
