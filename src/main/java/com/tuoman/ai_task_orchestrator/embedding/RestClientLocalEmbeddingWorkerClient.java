package com.tuoman.ai_task_orchestrator.embedding;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class RestClientLocalEmbeddingWorkerClient implements LocalEmbeddingWorkerClient {

    private final RestClient.Builder restClientBuilder;

    public RestClientLocalEmbeddingWorkerClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public LocalEmbeddingWorkerResponse createEmbeddings(
            LocalEmbeddingWorkerRequest request,
            EmbeddingProperties.LocalWorker properties
    ) {
        RestClient restClient = restClientBuilder
                .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                        java.net.http.HttpClient.newBuilder()
                                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                                .build()
                ))
                .build();

        return restClient.post()
                .uri("/embeddings")
                .body(request)
                .retrieve()
                .body(LocalEmbeddingWorkerResponse.class);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://127.0.0.1:8001";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
