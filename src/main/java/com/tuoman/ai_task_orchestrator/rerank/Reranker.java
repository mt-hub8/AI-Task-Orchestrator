package com.tuoman.ai_task_orchestrator.rerank;

public interface Reranker {

    RerankResponse rerank(RerankRequest request);

    String name();
}
