package com.tuoman.ai_task_orchestrator.service;

import com.tuoman.ai_task_orchestrator.entity.TaskOutboxEntity;
import com.tuoman.ai_task_orchestrator.enums.TaskOutboxStatus;
import com.tuoman.ai_task_orchestrator.repository.TaskOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskOutboxService {

    public static final String AGGREGATE_TYPE_TASK = "TASK";

    public static final String EVENT_TYPE_TASK_DISPATCH_REQUESTED = "TASK_DISPATCH_REQUESTED";

    private final TaskOutboxRepository taskOutboxRepository;

    @Transactional
    public TaskOutboxEntity createTaskDispatchOutbox(Long taskId) {
        TaskOutboxEntity outbox = new TaskOutboxEntity();
        outbox.setAggregateType(AGGREGATE_TYPE_TASK);
        outbox.setAggregateId(taskId);
        outbox.setEventType(EVENT_TYPE_TASK_DISPATCH_REQUESTED);
        outbox.setPayload("{\"taskId\":" + taskId + "}");
        outbox.setStatus(TaskOutboxStatus.PENDING);
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(null);
        return taskOutboxRepository.save(outbox);
    }
}
