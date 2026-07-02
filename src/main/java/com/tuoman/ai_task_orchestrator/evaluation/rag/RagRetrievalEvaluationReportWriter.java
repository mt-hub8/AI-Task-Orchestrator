package com.tuoman.ai_task_orchestrator.evaluation.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@Component
public class RagRetrievalEvaluationReportWriter {

    public static final String JSON_REPORT_NAME = "rag-retrieval-evaluation-report.json";

    public static final String MARKDOWN_REPORT_NAME = "rag-retrieval-evaluation-report.md";

    private final ObjectMapper objectMapper;

    public RagRetrievalEvaluationReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReportPaths write(RagRetrievalEvaluationReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        Path jsonPath = outputDir.resolve(JSON_REPORT_NAME);
        ObjectMapper prettyMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        prettyMapper.writeValue(jsonPath.toFile(), report);

        Path markdownPath = outputDir.resolve(MARKDOWN_REPORT_NAME);
        Files.writeString(markdownPath, toMarkdown(report));
        return new ReportPaths(jsonPath, markdownPath);
    }

    private String toMarkdown(RagRetrievalEvaluationReport report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# RAG Retrieval Evaluation Report\n\n");
        markdown.append("- **Dataset**: ").append(report.datasetName()).append('\n');
        markdown.append("- **Dataset path**: ").append(report.datasetPath()).append('\n');
        markdown.append("- **Run at**: ").append(report.runAt()).append('\n');
        markdown.append("- **Default TopK**: ").append(report.defaultTopK()).append('\n');
        markdown.append("- **Embedding provider**: ").append(report.embeddingProvider()).append('\n');
        markdown.append("- **Embedding model**: ").append(report.embeddingModel()).append('\n');
        markdown.append("- **Embedding dimension**: ").append(report.embeddingDimension()).append('\n');
        markdown.append("- **VectorStore**: ").append(report.vectorStore()).append("\n\n");

        markdown.append("## Summary Metrics\n\n");
        markdown.append("| totalCases | hitCount | HitRate@K | avg Recall@K | avg Precision@K | MRR | avg Latency (ms) |\n");
        markdown.append("|---:|---:|---:|---:|---:|---:|---:|\n");
        RagRetrievalSummaryMetrics summary = report.summary();
        markdown.append('|').append(summary.totalCases()).append('|');
        markdown.append(summary.hitCount()).append('|');
        markdown.append(format(summary.hitRateAtK())).append('|');
        markdown.append(format(summary.averageRecallAtK())).append('|');
        markdown.append(format(summary.averagePrecisionAtK())).append('|');
        markdown.append(format(summary.mrr())).append('|');
        markdown.append(format(summary.averageLatencyMs())).append("|\n\n");

        markdown.append("## Per-case Results\n\n");
        markdown.append("| caseId | topK | hit | recall | precision | rr | latencyMs | matched/expected |\n");
        markdown.append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (RagRetrievalCaseResult caseResult : report.cases()) {
            markdown.append('|').append(caseResult.caseId()).append('|');
            markdown.append(caseResult.topK()).append('|');
            markdown.append(caseResult.hit() ? "1" : "0").append('|');
            markdown.append(format(caseResult.recallAtK())).append('|');
            markdown.append(format(caseResult.precisionAtK())).append('|');
            markdown.append(format(caseResult.rrAtK())).append('|');
            markdown.append(caseResult.latencyMs()).append('|');
            markdown.append(caseResult.matchedExpectedItems().size()).append('/').append(caseResult.expectedItems().size()).append("|\n");
        }

        List<RagRetrievalCaseResult> missedCases = report.cases().stream()
                .filter(result -> !result.hit() || result.recallAtK() < 1.0)
                .toList();
        markdown.append("\n## Missed / Partial Cases\n\n");
        if (missedCases.isEmpty()) {
            markdown.append("- None. All cases hit expected items with recall=1.\n");
        } else {
            for (RagRetrievalCaseResult caseResult : missedCases) {
                markdown.append("- `").append(caseResult.caseId()).append("`");
                markdown.append(": hit=").append(caseResult.hit());
                markdown.append(", recall=").append(format(caseResult.recallAtK()));
                markdown.append(", precision=").append(format(caseResult.precisionAtK()));
                markdown.append(", rr=").append(format(caseResult.rrAtK())).append('\n');
            }
        }

        markdown.append("\n## Precision Definition\n\n");
        markdown.append("- 本报告中的 `Precision@K` 使用 `matched expected count / retrieved count`，");
        markdown.append("分母为该 case 实际返回结果条数，而非固定 K。\n");
        return markdown.toString();
    }

    private String format(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    public record ReportPaths(Path jsonPath, Path markdownPath) {
    }
}
