package com.tuoman.ai_task_orchestrator.evaluation.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Slf4j
@Component
@EnableConfigurationProperties(RagRetrievalEvaluationProperties.class)
@ConditionalOnProperty(prefix = "app.evaluation.retrieval", name = "enabled", havingValue = "true")
public class RagRetrievalEvaluationRunner implements ApplicationRunner {

    private final RagRetrievalEvaluationProperties properties;

    private final RagRetrievalEvaluationDatasetLoader datasetLoader;

    private final RagRetrievalEvaluationExecutor evaluationExecutor;

    private final RagRetrievalEvaluationReportWriter reportWriter;

    public RagRetrievalEvaluationRunner(
            RagRetrievalEvaluationProperties properties,
            RagRetrievalEvaluationDatasetLoader datasetLoader,
            RagRetrievalEvaluationExecutor evaluationExecutor,
            RagRetrievalEvaluationReportWriter reportWriter
    ) {
        this.properties = properties;
        this.datasetLoader = datasetLoader;
        this.evaluationExecutor = evaluationExecutor;
        this.reportWriter = reportWriter;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path datasetPath = Path.of(properties.getDatasetPath());
        RagRetrievalEvaluationDataset dataset = datasetLoader.load(datasetPath);

        RagRetrievalEvaluationReport report = evaluationExecutor.evaluate(
                dataset,
                datasetPath.toString(),
                properties.getDefaultTopK(),
                properties.getDocumentId()
        );

        RagRetrievalEvaluationReportWriter.ReportPaths reportPaths = reportWriter.write(
                report,
                Path.of(properties.getReportOutputDir())
        );
        log.info("RAG retrieval evaluation done, json={}, markdown={}", reportPaths.jsonPath(), reportPaths.markdownPath());
    }
}
