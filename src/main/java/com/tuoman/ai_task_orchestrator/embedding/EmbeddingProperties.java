package com.tuoman.ai_task_orchestrator.embedding;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {

    private String provider = MockEmbeddingClient.PROVIDER;

    private OpenAi openai = new OpenAi();

    private LocalWorker localWorker = new LocalWorker();

    @Getter
    @Setter
    public static class OpenAi {

        private String baseUrl = "https://api.openai.com/v1";

        private String apiKey = "";

        private String model = "text-embedding-3-small";

        private int timeoutMs = 10000;

        private Integer dimension = 1536;
    }

    @Getter
    @Setter
    public static class LocalWorker {

        private String baseUrl = "http://127.0.0.1:8001";

        private String model = "sentence-transformers/all-MiniLM-L6-v2";

        private Integer dimension = 384;

        private int timeoutMs = 10000;
    }
}
