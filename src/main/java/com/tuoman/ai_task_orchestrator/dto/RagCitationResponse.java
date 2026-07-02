package com.tuoman.ai_task_orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RagCitationResponse {

    private Integer sourceIndex;

    private Long documentId;

    private Long chunkId;

    private Double score;

    private String contentSnippet;

    private Integer originalRank;

    private Integer rerankedRank;

    private Double originalScore;

    private Double rerankScore;

    public RagCitationResponse(
            Integer sourceIndex,
            Long documentId,
            Long chunkId,
            Double score,
            String contentSnippet
    ) {
        this(sourceIndex, documentId, chunkId, score, contentSnippet, null, null, null, null);
    }
}
