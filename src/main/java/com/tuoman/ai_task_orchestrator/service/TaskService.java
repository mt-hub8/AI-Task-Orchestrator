package com.tuoman.ai_task_orchestrator.service;

import com.tuoman.ai_task_orchestrator.dto.CreateTaskRequest;
import com.tuoman.ai_task_orchestrator.dto.CreateTaskResponse;
import com.tuoman.ai_task_orchestrator.entity.TaskEntity;
import com.tuoman.ai_task_orchestrator.enums.TaskStatus;
import com.tuoman.ai_task_orchestrator.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    @Transactional
    public CreateTaskResponse createTask(CreateTaskRequest request) {
        TaskEntity task = new TaskEntity();
        task.setPrompt(request.getPrompt());
        task.setStatus(TaskStatus.PENDING);

        TaskEntity savedTask = taskRepository.save(task);

        return new CreateTaskResponse(savedTask.getId(), savedTask.getStatus());
    }
}