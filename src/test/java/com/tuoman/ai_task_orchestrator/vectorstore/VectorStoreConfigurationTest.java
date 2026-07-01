package com.tuoman.ai_task_orchestrator.vectorstore;

import com.tuoman.ai_task_orchestrator.repository.DocumentChunkEmbeddingRepository;
import com.tuoman.ai_task_orchestrator.repository.DocumentChunkRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class VectorStoreConfigurationTest {

    private final VectorStoreConfiguration configuration = new VectorStoreConfiguration();

    @Test
    void vectorStorePropertiesShouldDefaultToExact() {
        VectorStoreProperties properties = new VectorStoreProperties();

        assertThat(properties.getProvider()).isEqualTo("exact");
    }

    @Test
    void activeVectorStoreShouldUseExactWhenConfigured() {
        VectorStoreProperties properties = new VectorStoreProperties();
        properties.setProvider("exact");
        ExactCosineVectorStore exact = new ExactCosineVectorStore(
                mock(DocumentChunkEmbeddingRepository.class),
                mock(DocumentChunkRepository.class)
        );

        VectorStore vectorStore = configuration.activeVectorStore(properties, exact);

        assertThat(vectorStore).isSameAs(exact);
    }

    @Test
    void activeVectorStoreShouldFailForUnknownProvider() {
        VectorStoreProperties properties = new VectorStoreProperties();
        properties.setProvider("qdrant");
        ExactCosineVectorStore exact = new ExactCosineVectorStore(
                mock(DocumentChunkEmbeddingRepository.class),
                mock(DocumentChunkRepository.class)
        );

        assertThatThrownBy(() -> configuration.activeVectorStore(properties, exact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported vector store provider");
    }
}
