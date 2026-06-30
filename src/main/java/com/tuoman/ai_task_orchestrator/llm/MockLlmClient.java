package com.tuoman.ai_task_orchestrator.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockLlmClient implements LlmClient {

    private static final String DEFAULT_MODEL = "mock-llm";

    @Override
    public LlmResponse generate(LlmRequest request) {
        if (request == null) {
            log.info("Generate mock LLM response, taskId=null, model=null");
            return failureResponse(null, null, "LLM request is null");
        }

        String model = normalizeModel(request.getModel());
        log.info("Generate mock LLM response, taskId={}, model={}", request.getTaskId(), model);

        String prompt = request.getPrompt();
        if (prompt == null || prompt.isBlank()) {
            return failureResponse(request.getTaskId(), model, "Prompt is empty");
        }

        if (prompt.contains("fail") || prompt.contains("失败")) {
            log.warn("Mock LLM execution failed, taskId={}, model={}", request.getTaskId(), model);
            return failureResponse(request.getTaskId(), model, "Mock LLM execution failed");
        }

        LlmResponse response = new LlmResponse();
        response.setTaskId(request.getTaskId());
        response.setModel(model);
        response.setContent("Mock LLM response for prompt: " + prompt);
        response.setSuccess(true);
        response.setErrorMessage(null);

        log.info("Mock LLM response generated, taskId={}, model={}", request.getTaskId(), model);
        return response;
    }

    private LlmResponse failureResponse(Long taskId, String model, String errorMessage) {
        LlmResponse response = new LlmResponse();
        response.setTaskId(taskId);
        response.setModel(model);
        response.setContent(null);
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }

    private String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return DEFAULT_MODEL;
        }
        return model;
    }
}
