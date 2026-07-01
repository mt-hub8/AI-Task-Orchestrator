package com.tuoman.ai_task_orchestrator.vectorstore;

import com.tuoman.ai_task_orchestrator.repository.DocumentChunkEmbeddingRepository;
import com.tuoman.ai_task_orchestrator.repository.DocumentChunkRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(VectorStoreProperties.class)
public class VectorStoreConfiguration {

    @Bean
    public ExactCosineVectorStore exactCosineVectorStore(
            DocumentChunkEmbeddingRepository documentChunkEmbeddingRepository,
            DocumentChunkRepository documentChunkRepository
    ) {
        return new ExactCosineVectorStore(documentChunkEmbeddingRepository, documentChunkRepository);
    }

    @Bean
    @Primary
    public VectorStore activeVectorStore(
            VectorStoreProperties properties,
            ExactCosineVectorStore exactCosineVectorStore
    ) {
        String provider = properties.getProvider();
        if (ExactCosineVectorStore.PROVIDER.equalsIgnoreCase(provider)) {
            return exactCosineVectorStore;
        }
        throw new IllegalArgumentException("Unsupported vector store provider: " + provider);
    }
}
