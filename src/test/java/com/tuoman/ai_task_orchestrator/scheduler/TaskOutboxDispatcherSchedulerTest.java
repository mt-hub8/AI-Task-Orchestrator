package com.tuoman.ai_task_orchestrator.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoman.ai_task_orchestrator.entity.TaskOutboxEntity;
import com.tuoman.ai_task_orchestrator.enums.TaskOutboxStatus;
import com.tuoman.ai_task_orchestrator.mq.TaskDispatchProducer;
import com.tuoman.ai_task_orchestrator.repository.TaskOutboxRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskOutboxDispatcherSchedulerTest {

    private final TaskOutboxRepository taskOutboxRepository = mock(TaskOutboxRepository.class);

    private final TaskDispatchProducer taskDispatchProducer = mock(TaskDispatchProducer.class);

    private final TaskOutboxDispatcherScheduler scheduler = new TaskOutboxDispatcherScheduler(
            taskOutboxRepository,
            taskDispatchProducer,
            new ObjectMapper()
    );

    @Test
    void dueOutboxShouldBeSentAndMarkedSent() {
        TaskOutboxEntity outbox = outbox(1L, 101L);
        when(taskOutboxRepository.claimOutbox(
                eq(1L),
                eq(TaskOutboxStatus.PROCESSING),
                anyCollection(),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(1);
        when(taskOutboxRepository.findById(1L)).thenReturn(Optional.of(outbox));

        scheduler.dispatchSingleOutbox(1L);

        verify(taskDispatchProducer).sendTaskCreatedMessage(101L);
        verify(taskOutboxRepository).markSent(
                eq(1L),
                eq(TaskOutboxStatus.PROCESSING),
                eq(TaskOutboxStatus.SENT),
                any(LocalDateTime.class)
        );
    }

    @Test
    void sendFailureShouldMarkOutboxFailed() {
        TaskOutboxEntity outbox = outbox(2L, 102L);
        when(taskOutboxRepository.claimOutbox(
                eq(2L),
                eq(TaskOutboxStatus.PROCESSING),
                anyCollection(),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(1);
        when(taskOutboxRepository.findById(2L)).thenReturn(Optional.of(outbox));
        org.mockito.Mockito.doThrow(new RuntimeException("rabbit down"))
                .when(taskDispatchProducer)
                .sendTaskCreatedMessage(102L);

        scheduler.dispatchSingleOutbox(2L);

        verify(taskOutboxRepository).markFailed(
                eq(2L),
                eq(TaskOutboxStatus.PROCESSING),
                eq(TaskOutboxStatus.FAILED),
                any(LocalDateTime.class),
                eq("rabbit down"),
                any(LocalDateTime.class)
        );
    }

    @Test
    void claimFailureShouldNotSendMessage() {
        when(taskOutboxRepository.claimOutbox(
                eq(3L),
                eq(TaskOutboxStatus.PROCESSING),
                anyCollection(),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(0);

        scheduler.dispatchSingleOutbox(3L);

        verify(taskDispatchProducer, never()).sendTaskCreatedMessage(any());
        verify(taskOutboxRepository, never()).markSent(any(), any(), any(), any());
        verify(taskOutboxRepository, never()).markFailed(any(), any(), any(), any(), any(), any());
    }

    @Test
    void dispatchDueOutboxesShouldProcessBatch() {
        when(taskOutboxRepository.findDueOutboxes(
                eq(List.of(TaskOutboxStatus.PENDING, TaskOutboxStatus.FAILED)),
                any(LocalDateTime.class),
                any()
        )).thenReturn(List.of(outbox(4L, 104L)));
        when(taskOutboxRepository.claimOutbox(
                eq(4L),
                eq(TaskOutboxStatus.PROCESSING),
                anyCollection(),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(1);
        when(taskOutboxRepository.findById(4L)).thenReturn(Optional.of(outbox(4L, 104L)));

        scheduler.dispatchDueOutboxes();

        verify(taskDispatchProducer).sendTaskCreatedMessage(104L);
    }

    private TaskOutboxEntity outbox(Long id, Long taskId) {
        TaskOutboxEntity outbox = new TaskOutboxEntity();
        outbox.setId(id);
        outbox.setAggregateType("TASK");
        outbox.setAggregateId(taskId);
        outbox.setEventType("TASK_DISPATCH_REQUESTED");
        outbox.setPayload("{\"taskId\":" + taskId + "}");
        outbox.setStatus(TaskOutboxStatus.PENDING);
        outbox.setRetryCount(0);
        return outbox;
    }
}
