package com.tuoman.ai_task_orchestrator.service;

import com.tuoman.ai_task_orchestrator.dto.CreateTaskRequest;
import com.tuoman.ai_task_orchestrator.dto.CreateTaskResponse;
import com.tuoman.ai_task_orchestrator.dto.TaskDetailResponse;
import com.tuoman.ai_task_orchestrator.entity.TaskEntity;
import com.tuoman.ai_task_orchestrator.entity.TaskEventEntity;
import com.tuoman.ai_task_orchestrator.enums.TaskEventType;
import com.tuoman.ai_task_orchestrator.enums.TaskStatus;
import com.tuoman.ai_task_orchestrator.llm.LlmResponse;
import com.tuoman.ai_task_orchestrator.repository.TaskEventRepository;
import com.tuoman.ai_task_orchestrator.repository.TaskRepository;
import com.tuoman.ai_task_orchestrator.state.TaskStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private static final String TIMEOUT_ERROR_MESSAGE = "任务执行超时";

    private static final String DEFAULT_ERROR_MESSAGE = "未知错误";

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final TaskRepository taskRepository;

    private final TaskEventRepository taskEventRepository;

    private final TaskStateMachine taskStateMachine;

    private final TaskOutboxService taskOutboxService;

    private final TaskAttemptService taskAttemptService;

    private final TaskOutputChunkService taskOutputChunkService;

    @Transactional
    public CreateTaskResponse createTask(CreateTaskRequest request) {
        TaskEntity task = new TaskEntity();
        task.setPrompt(request.getPrompt());
        task.setRequestedModel(request.getModel());
        task.setStatus(TaskStatus.PENDING);
        task.setRetryCount(0);
        task.setMaxRetry(3);
        task.setNextRetryAt(null);
        task.setTimeoutSeconds(30);
        task.setTimeoutAt(null);

        TaskEntity savedTask = taskRepository.save(task);

        recordTaskEvent(
                savedTask.getId(),
                TaskEventType.TASK_CREATED,
                null,
                TaskStatus.PENDING,
                "任务创建成功"
        );

        taskOutboxService.createTaskDispatchOutbox(savedTask.getId());

        return new CreateTaskResponse(savedTask.getId(), savedTask.getStatus());
    }

    @Transactional(readOnly = true)
    public TaskDetailResponse getTaskById(Long taskId) {
        TaskEntity task = findTaskOrThrow(taskId);
        return toTaskDetailResponse(task);
    }

    @Transactional
    public TaskDetailResponse updateTaskStatus(Long taskId, TaskStatus targetStatus, String message) {
        TaskEntity task = findTaskOrThrow(taskId);

        TaskStatus currentStatus = task.getStatus();

        if (!taskStateMachine.canTransit(currentStatus, targetStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "非法状态流转：" + currentStatus + " -> " + targetStatus
            );
        }

        task.setStatus(targetStatus);

        TaskEntity savedTask = taskRepository.save(task);

        recordTaskEvent(
                savedTask.getId(),
                TaskEventType.STATUS_CHANGED,
                currentStatus,
                targetStatus,
                message
        );

        return toTaskDetailResponse(savedTask);
    }

    @Transactional
    public boolean tryStartTaskExecution(Long taskId, String message) {
        TaskEntity task = findTaskOrThrow(taskId);
        TaskStatus targetStatus = TaskStatus.RUNNING;
        int timeoutSeconds = task.getTimeoutSeconds() == null ? 30 : task.getTimeoutSeconds();
        LocalDateTime timeoutAt = LocalDateTime.now().plusSeconds(timeoutSeconds);
        TaskStatus claimedFromStatus = claimTaskForExecution(taskId, targetStatus, timeoutAt);

        if (claimedFromStatus == null) {
            return false;
        }

        recordTaskEvent(
                taskId,
                TaskEventType.STATUS_CHANGED,
                claimedFromStatus,
                targetStatus,
                message
        );

        return true;
    }

    private TaskStatus claimTaskForExecution(Long taskId, TaskStatus targetStatus, LocalDateTime timeoutAt) {
        if (taskStateMachine.canTransit(TaskStatus.PENDING, targetStatus)) {
            int updated = taskRepository.claimTaskForExecution(
                    taskId,
                    targetStatus,
                    timeoutAt,
                    List.of(TaskStatus.PENDING)
            );
            if (updated == 1) {
                return TaskStatus.PENDING;
            }
        }

        if (taskStateMachine.canTransit(TaskStatus.RETRY_PENDING, targetStatus)) {
            int updated = taskRepository.claimTaskForExecution(
                    taskId,
                    targetStatus,
                    timeoutAt,
                    List.of(TaskStatus.RETRY_PENDING)
            );
            if (updated == 1) {
                return TaskStatus.RETRY_PENDING;
            }
        }

        return null;
    }

    @Transactional
    public boolean tryMarkTaskFailed(Long taskId, String errorMessage) {
        String normalizedErrorMessage = normalizeErrorMessage(errorMessage);
        int updated = taskRepository.markFailedIfRunning(
                taskId,
                TaskStatus.FAILED,
                TaskStatus.RUNNING,
                normalizedErrorMessage
        );

        if (updated != 1) {
            log.info("task_failed_finalization_rejected taskId={}", taskId);
            return false;
        }

        recordTaskEvent(
                taskId,
                TaskEventType.STATUS_CHANGED,
                TaskStatus.RUNNING,
                TaskStatus.FAILED,
                normalizedErrorMessage
        );

        return true;
    }

    @Transactional
    public boolean finalizeSuccessfulExecution(
            Long taskId,
            Long attemptId,
            LlmResponse response,
            String renderedPrompt,
            String promptTemplateCode
    ) {
        int updated = taskRepository.markSucceededIfRunning(
                taskId,
                TaskStatus.SUCCESS,
                TaskStatus.RUNNING,
                response.getContent(),
                response.getModel(),
                renderedPrompt,
                promptTemplateCode
        );

        if (updated != 1) {
            log.info("task_success_finalization_rejected taskId={}", taskId);
            return false;
        }

        recordTaskEvent(
                taskId,
                TaskEventType.STATUS_CHANGED,
                TaskStatus.RUNNING,
                TaskStatus.SUCCESS,
                "LLM 任务执行成功"
        );

        taskAttemptService.markSuccess(attemptId, response, renderedPrompt, promptTemplateCode);
        taskOutputChunkService.saveChunks(taskId, response.getContent());
        return true;
    }

    @Transactional
    public boolean tryMarkTaskRetryPending(Long taskId, String errorMessage) {
        String normalizedErrorMessage = normalizeErrorMessage(errorMessage);
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(10);
        int updated = taskRepository.markRetryPendingIfRunning(
                taskId,
                TaskStatus.RETRY_PENDING,
                TaskStatus.RUNNING,
                nextRetryAt,
                normalizedErrorMessage
        );

        if (updated != 1) {
            log.info("task_retry_pending_finalization_rejected taskId={}", taskId);
            return false;
        }

        TaskEntity task = findTaskOrThrow(taskId);
        recordTaskEvent(
                taskId,
                TaskEventType.STATUS_CHANGED,
                TaskStatus.RUNNING,
                TaskStatus.RETRY_PENDING,
                "任务执行失败，等待第 " + task.getRetryCount() + " 次重试：" + normalizedErrorMessage
        );

        return true;
    }

    @Transactional
    public void saveLlmMetadata(
            Long taskId,
            String llmProvider,
            String llmModel,
            Integer promptTokenCount,
            Integer completionTokenCount,
            Integer totalTokenCount,
            Long llmLatencyMs
    ) {
        TaskEntity task = findTaskOrThrow(taskId);
        task.setLlmProvider(llmProvider);
        task.setLlmModel(llmModel);
        task.setPromptTokenCount(promptTokenCount);
        task.setCompletionTokenCount(completionTokenCount);
        task.setTotalTokenCount(totalTokenCount);
        task.setLlmLatencyMs(llmLatencyMs);
        taskRepository.save(task);
    }

    @Transactional
    public TaskDetailResponse cancelTask(Long taskId, String message) {
        TaskEntity task = findTaskOrThrow(taskId);
        TaskStatus currentStatus = task.getStatus();

        if (currentStatus != TaskStatus.PENDING
                && currentStatus != TaskStatus.RETRY_PENDING
                && currentStatus != TaskStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前任务状态不允许取消");
        }

        int updated = taskRepository.markCancelledIfAllowed(
                taskId,
                TaskStatus.CANCELLED,
                List.of(currentStatus)
        );

        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前任务状态不允许取消");
        }

        recordTaskEvent(
                taskId,
                TaskEventType.STATUS_CHANGED,
                currentStatus,
                TaskStatus.CANCELLED,
                message
        );

        return toTaskDetailResponse(findTaskOrThrow(taskId));
    }

    @Transactional(readOnly = true)
    public boolean isTaskCancelled(Long taskId) {
        TaskEntity task = findTaskOrThrow(taskId);
        return task.getStatus() == TaskStatus.CANCELLED;
    }

    @Transactional(readOnly = true)
    public boolean isTaskRunning(Long taskId) {
        TaskEntity task = findTaskOrThrow(taskId);
        return task.getStatus() == TaskStatus.RUNNING;
    }

    @Transactional
    public boolean tryMarkTaskTimedOut(Long taskId) {
        int updated = taskRepository.markTimedOutIfRunning(
                taskId,
                TaskStatus.FAILED,
                TaskStatus.RUNNING,
                TIMEOUT_ERROR_MESSAGE
        );

        if (updated != 1) {
            log.info("task_timeout_finalization_rejected taskId={}", taskId);
            return false;
        }

        recordTaskEvent(
                taskId,
                TaskEventType.STATUS_CHANGED,
                TaskStatus.RUNNING,
                TaskStatus.FAILED,
                TIMEOUT_ERROR_MESSAGE
        );

        return true;
    }

    private String normalizeErrorMessage(String errorMessage) {
        String normalizedErrorMessage = errorMessage;

        if (normalizedErrorMessage == null || normalizedErrorMessage.isBlank()) {
            normalizedErrorMessage = DEFAULT_ERROR_MESSAGE;
        }

        if (normalizedErrorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
            return normalizedErrorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
        }

        return normalizedErrorMessage;
    }

    private void recordTaskEvent(
            Long taskId,
            TaskEventType eventType,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            String message
    ) {
        TaskEventEntity event = new TaskEventEntity();
        event.setTaskId(taskId);
        event.setEventType(eventType);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setMessage(message);

        taskEventRepository.save(event);
    }

    private TaskEntity findTaskOrThrow(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private TaskDetailResponse toTaskDetailResponse(TaskEntity task) {
        return new TaskDetailResponse(
                task.getId(),
                task.getPrompt(),
                task.getRequestedModel(),
                task.getStatus(),
                task.getErrorMessage(),
                task.getRetryCount(),
                task.getMaxRetry(),
                task.getNextRetryAt(),
                task.getTimeoutSeconds(),
                task.getTimeoutAt(),
                task.getResultContent(),
                task.getLlmModel(),
                task.getRenderedPrompt(),
                task.getPromptTemplateCode(),
                task.getLlmProvider(),
                task.getPromptTokenCount(),
                task.getCompletionTokenCount(),
                task.getTotalTokenCount(),
                task.getLlmLatencyMs(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
