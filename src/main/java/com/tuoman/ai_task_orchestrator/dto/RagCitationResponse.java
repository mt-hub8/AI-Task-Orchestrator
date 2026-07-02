package com.tuoman.ai_task_orchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RagCitationResponse {

    private Integer sourceIndex;

    private Long documentId;

    private Long chunkId;

    private Double score;

    private String contentSnippet;
}
