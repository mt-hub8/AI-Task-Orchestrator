package com.tuoman.ai_task_orchestrator.rerank;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(RagRerankProperties.class)
public class RerankerConfiguration {

    @Bean
    @Primary
    public Reranker activeReranker(
            RagRerankProperties properties,
            LexicalOverlapReranker lexicalOverlapReranker
    ) {
        if (LexicalOverlapReranker.PROVIDER.equalsIgnoreCase(properties.getProvider())) {
            return lexicalOverlapReranker;
        }
        return lexicalOverlapReranker;
    }
}
