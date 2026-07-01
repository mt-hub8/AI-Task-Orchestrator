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

    @Getter
    @Setter
    public static class OpenAi {

        private String baseUrl = "https://api.openai.com/v1";

        private String apiKey = "";

        private String model = "text-embedding-3-small";

        private int timeoutMs = 10000;

        private Integer dimension = 1536;
    }
}
