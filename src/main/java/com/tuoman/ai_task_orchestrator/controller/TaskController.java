package com.tuoman.ai_task_orchestrator.controller;

import com.tuoman.ai_task_orchestrator.dto.CreateTaskRequest;
import com.tuoman.ai_task_orchestrator.dto.CreateTaskResponse;
import com.tuoman.ai_task_orchestrator.dto.TaskDetailResponse;
import com.tuoman.ai_task_orchestrator.dto.TaskOutputChunkResponse;
import com.tuoman.ai_task_orchestrator.dto.UpdateTaskStatusRequest;
import com.tuoman.ai_task_orchestrator.service.TaskOutputChunkService;
import com.tuoman.ai_task_orchestrator.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    private final TaskOutputChunkService taskOutputChunkService;

    @PostMapping
    public CreateTaskResponse createTask(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.createTask(request);
    }

    @GetMapping("/{taskId}")
    public TaskDetailResponse getTaskById(@PathVariable Long taskId) {
        return taskService.getTaskById(taskId);
    }

    @PatchMapping("/{taskId}/status")
    public TaskDetailResponse updateTaskStatus(
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskStatusRequest request
    ) {
        return taskService.updateTaskStatus(taskId, request.getStatus(), request.getMessage());
    }

    @PostMapping("/{taskId}/cancel")
    public TaskDetailResponse cancelTask(@PathVariable Long taskId) {
        return taskService.cancelTask(taskId, "任务已取消");
    }

    @GetMapping("/{taskId}/output-chunks")
    public List<TaskOutputChunkResponse> getOutputChunks(@PathVariable Long taskId) {
        return taskOutputChunkService.getChunks(taskId);
    }
}
