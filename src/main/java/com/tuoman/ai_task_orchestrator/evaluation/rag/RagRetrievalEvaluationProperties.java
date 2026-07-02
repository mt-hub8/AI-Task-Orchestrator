package com.tuoman.ai_task_orchestrator.evaluation.rag;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.evaluation.retrieval")
public class RagRetrievalEvaluationProperties {

    private boolean enabled = false;

    private String datasetPath = "docs/evaluation/rag-retrieval-eval-cases.json";

    private String reportOutputDir = "docs/evaluation/reports";

    private int defaultTopK = 5;

    private Long documentId;
}
