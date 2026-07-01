package com.tuoman.ai_task_orchestrator.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    private final HttpStatus httpStatus;

    public BusinessException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public static BusinessException taskNotFound() {
        return new BusinessException(ErrorCode.TASK_NOT_FOUND, HttpStatus.NOT_FOUND, "任务不存在");
    }

    public static BusinessException documentNotFound() {
        return new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Document not found");
    }

    public static BusinessException invalidTaskStatus(String message) {
        return new BusinessException(ErrorCode.INVALID_TASK_STATUS, HttpStatus.CONFLICT, message);
    }

    public static BusinessException taskFinalizationRejected(String message) {
        return new BusinessException(ErrorCode.TASK_FINALIZATION_REJECTED, HttpStatus.CONFLICT, message);
    }

    public static BusinessException invalidRequest(String message) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, message);
    }

    public static BusinessException retrievalEvaluationError(String message) {
        return new BusinessException(ErrorCode.RETRIEVAL_EVALUATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    public static BusinessException internalError(String message) {
        return new BusinessException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
