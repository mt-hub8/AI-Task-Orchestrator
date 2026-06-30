package com.tuoman.ai_task_orchestrator.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class ModelRouter {

    private static final String DEFAULT_MODEL = "mock-llm";

    private static final Set<String> SUPPORTED_MODELS = Set.of(
            "mock-llm",
            "mock-fast",
            "mock-smart"
    );

    public String route(String requestedModel) {
        if (requestedModel == null || requestedModel.isBlank()) {
            return DEFAULT_MODEL;
        }

        if (SUPPORTED_MODELS.contains(requestedModel)) {
            return requestedModel;
        }

        log.warn("Unsupported requested model, fallback to default model, requestedModel={}, defaultModel={}",
                requestedModel,
                DEFAULT_MODEL);
        return DEFAULT_MODEL;
    }
}
