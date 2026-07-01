package com.tuoman.ai_task_orchestrator.vectorstore;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.vector-store")
public class VectorStoreProperties {

    private String provider = ExactCosineVectorStore.PROVIDER;
}
