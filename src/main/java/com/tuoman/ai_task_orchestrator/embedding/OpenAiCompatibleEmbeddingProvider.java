package com.tuoman.ai_task_orchestrator.embedding;

import java.util.Comparator;
import java.util.List;

public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {

    public static final String PROVIDER = "openai";

    public static final String DISTANCE_METRIC = "COSINE";

    private final EmbeddingProperties.OpenAi properties;

    private final OpenAiEmbeddingHttpClient httpClient;

    public OpenAiCompatibleEmbeddingProvider(
            EmbeddingProperties.OpenAi properties,
            OpenAiEmbeddingHttpClient httpClient
    ) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        return embedBatch(List.of(request == null ? new EmbeddingRequest() : request)).getFirst();
    }

    @Override
    public List<EmbeddingResponse> embedBatch(List<EmbeddingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        validateApiKey();

        List<String> inputs = requests.stream()
                .map(request -> request == null ? null : request.getText())
                .map(text -> text == null ? "" : text)
                .toList();

        OpenAiEmbeddingRequest openAiRequest = new OpenAiEmbeddingRequest(model(), inputs);
        OpenAiEmbeddingResponse response;
        try {
            response = httpClient.createEmbeddings(openAiRequest, properties);
        } catch (EmbeddingProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EmbeddingProviderException("OpenAI-compatible embedding request failed", exception);
        }

        validateResponse(response, inputs.size());

        return response.getData().stream()
                .sorted(Comparator.comparing(OpenAiEmbeddingResponse.EmbeddingData::getIndex))
                .map(data -> toEmbeddingResponse(data.getEmbedding()))
                .toList();
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String model() {
        return properties.getModel();
    }

    @Override
    public int dimension() {
        return properties.getDimension() == null ? 0 : properties.getDimension();
    }

    private EmbeddingResponse toEmbeddingResponse(List<Double> vector) {
        validateVector(vector);
        return new EmbeddingResponse(PROVIDER, model(), vector.size(), DISTANCE_METRIC, vector);
    }

    private void validateApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new EmbeddingProviderException("OpenAI-compatible embedding api key must not be blank");
        }
    }

    private void validateResponse(OpenAiEmbeddingResponse response, int expectedCount) {
        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            throw new EmbeddingProviderException("OpenAI-compatible embedding response data must not be empty");
        }
        if (response.getData().size() != expectedCount) {
            throw new EmbeddingProviderException("OpenAI-compatible embedding response size does not match input size");
        }
    }

    private void validateVector(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            throw new EmbeddingProviderException("OpenAI-compatible embedding vector must not be empty");
        }
        if (properties.getDimension() != null && properties.getDimension() > 0
                && vector.size() != properties.getDimension()) {
            throw new EmbeddingProviderException("OpenAI-compatible embedding vector dimension mismatch");
        }
    }
}
