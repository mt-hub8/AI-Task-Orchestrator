package com.tuoman.ai_task_orchestrator.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoman.ai_task_orchestrator.entity.TaskOutboxEntity;
import com.tuoman.ai_task_orchestrator.enums.TaskOutboxStatus;
import com.tuoman.ai_task_orchestrator.mq.TaskDispatchProducer;
import com.tuoman.ai_task_orchestrator.repository.TaskOutboxRepository;
import com.tuoman.ai_task_orchestrator.service.TaskOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskOutboxDispatcherScheduler {

    private static final int BATCH_SIZE = 20;

    private static final int RETRY_DELAY_SECONDS = 30;

    private static final int LOCK_STALE_SECONDS = 60;

    private final TaskOutboxRepository taskOutboxRepository;

    private final TaskDispatchProducer taskDispatchProducer;

    private final ObjectMapper objectMapper;

    private final String dispatcherId = ManagementFactory.getRuntimeMXBean().getName();

    @Transactional
    @Scheduled(fixedDelay = 5000)
    public void dispatchDueOutboxes() {
        LocalDateTime now = LocalDateTime.now();
        List<TaskOutboxEntity> outboxes = taskOutboxRepository.findDueOutboxes(
                List.of(TaskOutboxStatus.PENDING, TaskOutboxStatus.FAILED),
                now,
                PageRequest.of(0, BATCH_SIZE)
        );

        log.info("Found due task outboxes count={}", outboxes.size());

        for (TaskOutboxEntity outbox : outboxes) {
            dispatchSingleOutbox(outbox.getId());
        }
    }

    @Transactional
    public void dispatchSingleOutbox(Long outboxId) {
        LocalDateTime now = LocalDateTime.now();
        int claimed = taskOutboxRepository.claimOutbox(
                outboxId,
                TaskOutboxStatus.PROCESSING,
                List.of(TaskOutboxStatus.PENDING, TaskOutboxStatus.FAILED),
                dispatcherId,
                now,
                now,
                now.minusSeconds(LOCK_STALE_SECONDS)
        );

        if (claimed != 1) {
            log.info("Skip task outbox because claim failed, outboxId={}", outboxId);
            return;
        }

        TaskOutboxEntity outbox = taskOutboxRepository.findById(outboxId).orElseThrow();
        Long taskId = extractTaskId(outbox.getPayload());

        try {
            if (!TaskOutboxService.EVENT_TYPE_TASK_DISPATCH_REQUESTED.equals(outbox.getEventType())) {
                throw new IllegalStateException("Unsupported task outbox event type: " + outbox.getEventType());
            }

            log.info(
                    "Dispatch task outbox, outboxId={}, taskId={}, eventType={}",
                    outboxId,
                    taskId,
                    outbox.getEventType()
            );

            taskDispatchProducer.sendTaskCreatedMessage(taskId);
            taskOutboxRepository.markSent(
                    outboxId,
                    TaskOutboxStatus.PROCESSING,
                    TaskOutboxStatus.SENT,
                    LocalDateTime.now()
            );
            log.info("Task outbox sent, outboxId={}, taskId={}", outboxId, taskId);
        } catch (Exception e) {
            LocalDateTime retryAt = LocalDateTime.now().plusSeconds(RETRY_DELAY_SECONDS);
            taskOutboxRepository.markFailed(
                    outboxId,
                    TaskOutboxStatus.PROCESSING,
                    TaskOutboxStatus.FAILED,
                    retryAt,
                    normalizeErrorMessage(e.getMessage()),
                    LocalDateTime.now()
            );
            log.error("Task outbox dispatch failed, outboxId={}, taskId={}, nextRetryAt={}", outboxId, taskId, retryAt, e);
        }
    }

    private Long extractTaskId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode taskIdNode = root.get("taskId");
            if (taskIdNode == null || !taskIdNode.canConvertToLong()) {
                throw new IllegalArgumentException("Missing taskId in outbox payload");
            }
            return taskIdNode.asLong();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid task outbox payload", e);
        }
    }

    private String normalizeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown outbox dispatch error";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
