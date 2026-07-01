package com.tuoman.ai_task_orchestrator.controller;

import com.tuoman.ai_task_orchestrator.dto.RetrievalEvaluationRequest;
import com.tuoman.ai_task_orchestrator.dto.RetrievalEvaluationResponse;
import com.tuoman.ai_task_orchestrator.service.RetrievalEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/evaluations")
@RequiredArgsConstructor
public class RetrievalEvaluationController {

    private final RetrievalEvaluationService retrievalEvaluationService;

    @PostMapping("/retrieval")
    public RetrievalEvaluationResponse evaluate(@RequestBody RetrievalEvaluationRequest request) {
        return retrievalEvaluationService.evaluate(request);
    }
}
