package com.tuoman.ai_task_orchestrator.embedding;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingProviderConfiguration {

    @Bean
    public OpenAiCompatibleEmbeddingProvider openAiCompatibleEmbeddingProvider(
            EmbeddingProperties properties,
            OpenAiEmbeddingHttpClient httpClient
    ) {
        return new OpenAiCompatibleEmbeddingProvider(properties.getOpenai(), httpClient);
    }

    @Bean
    @Primary
    public EmbeddingProvider activeEmbeddingProvider(
            EmbeddingProperties properties,
            MockEmbeddingClient mockEmbeddingClient,
            OpenAiCompatibleEmbeddingProvider openAiCompatibleEmbeddingProvider
    ) {
        String provider = properties.getProvider();
        if (OpenAiCompatibleEmbeddingProvider.PROVIDER.equalsIgnoreCase(provider)) {
            return openAiCompatibleEmbeddingProvider;
        }
        return mockEmbeddingClient;
    }
}
