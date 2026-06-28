package com.tuoman.ai_task_orchestrator.service;

import com.tuoman.ai_task_orchestrator.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TaskService taskService;

    public void executeTask(Long taskId) {
        log.info("Start executing task, taskId={}", taskId);

        taskService.updateTaskStatus(
                taskId,
                TaskStatus.RUNNING,
                "任务开始执行"
        );

        simulateTaskExecution(taskId);

        taskService.updateTaskStatus(
                taskId,
                TaskStatus.SUCCESS,
                "任务执行成功"
        );

        log.info("Finish executing task, taskId={}", taskId);
    }

    private void simulateTaskExecution(Long taskId) {
        try {
            log.info("Simulating task execution, taskId={}", taskId);
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("任务执行被中断", e);
        }
    }
}