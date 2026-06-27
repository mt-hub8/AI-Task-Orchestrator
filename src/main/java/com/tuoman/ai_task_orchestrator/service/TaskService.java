package com.tuoman.ai_task_orchestrator.service;

import com.tuoman.ai_task_orchestrator.mq.TaskDispatchProducer;
import com.tuoman.ai_task_orchestrator.dto.CreateTaskRequest;
import com.tuoman.ai_task_orchestrator.dto.CreateTaskResponse;
import com.tuoman.ai_task_orchestrator.dto.TaskDetailResponse;
import com.tuoman.ai_task_orchestrator.entity.TaskEntity;
import com.tuoman.ai_task_orchestrator.entity.TaskEventEntity;
import com.tuoman.ai_task_orchestrator.enums.TaskEventType;
import com.tuoman.ai_task_orchestrator.enums.TaskStatus;
import com.tuoman.ai_task_orchestrator.repository.TaskEventRepository;
import com.tuoman.ai_task_orchestrator.repository.TaskRepository;
import com.tuoman.ai_task_orchestrator.state.TaskStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    private final TaskEventRepository taskEventRepository;

    private final TaskStateMachine taskStateMachine;

    @Transactional
    public CreateTaskResponse createTask(CreateTaskRequest request) {
        TaskEntity task = new TaskEntity();
        task.setPrompt(request.getPrompt());
        task.setStatus(TaskStatus.PENDING);

        TaskEntity savedTask = taskRepository.save(task);

        recordTaskEvent(
                savedTask.getId(),
                TaskEventType.TASK_CREATED,
                null,
                TaskStatus.PENDING,
                "任务创建成功"
        );

        taskDispatchProducer.sendTaskCreatedMessage(savedTask.getId());

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
                task.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
    private final TaskDispatchProducer taskDispatchProducer;
}