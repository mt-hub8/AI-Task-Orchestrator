package com.tuoman.ai_task_orchestrator.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingProviderConfigurationTest {

    private final EmbeddingProviderConfiguration configuration = new EmbeddingProviderConfiguration();

    @Test
    void activeEmbeddingProviderShouldDefaultToMock() {
        EmbeddingProperties properties = new EmbeddingProperties();
        MockEmbeddingClient mock = new MockEmbeddingClient();
        OpenAiCompatibleEmbeddingProvider openAi = new OpenAiCompatibleEmbeddingProvider(
                properties.getOpenai(),
                (request, openAiProperties) -> new OpenAiEmbeddingResponse()
        );

        EmbeddingProvider provider = configuration.activeEmbeddingProvider(properties, mock, openAi);

        assertThat(provider).isSameAs(mock);
        assertThat(provider.provider()).isEqualTo("mock");
    }

    @Test
    void activeEmbeddingProviderShouldUseOpenAiWhenConfigured() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setProvider("openai");
        MockEmbeddingClient mock = new MockEmbeddingClient();
        OpenAiCompatibleEmbeddingProvider openAi = new OpenAiCompatibleEmbeddingProvider(
                properties.getOpenai(),
                (request, openAiProperties) -> new OpenAiEmbeddingResponse()
        );

        EmbeddingProvider provider = configuration.activeEmbeddingProvider(properties, mock, openAi);

        assertThat(provider).isSameAs(openAi);
        assertThat(provider.provider()).isEqualTo("openai");
    }
}
