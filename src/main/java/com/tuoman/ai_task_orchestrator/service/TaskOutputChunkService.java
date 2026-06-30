package com.tuoman.ai_task_orchestrator.service;

import com.tuoman.ai_task_orchestrator.dto.TaskOutputChunkResponse;
import com.tuoman.ai_task_orchestrator.entity.TaskOutputChunkEntity;
import com.tuoman.ai_task_orchestrator.repository.TaskOutputChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskOutputChunkService {

    private static final int CHUNK_SIZE = 30;

    private final TaskOutputChunkRepository taskOutputChunkRepository;

    @Transactional
    public void saveChunks(Long taskId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        List<TaskOutputChunkEntity> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (int start = 0; start < content.length(); start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, content.length());

            TaskOutputChunkEntity chunk = new TaskOutputChunkEntity();
            chunk.setTaskId(taskId);
            chunk.setChunkIndex(chunkIndex);
            chunk.setContent(content.substring(start, end));
            chunks.add(chunk);

            chunkIndex++;
        }

        taskOutputChunkRepository.saveAll(chunks);
    }

    @Transactional(readOnly = true)
    public List<TaskOutputChunkResponse> getChunks(Long taskId) {
        return taskOutputChunkRepository.findByTaskIdOrderByChunkIndexAsc(taskId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private TaskOutputChunkResponse toResponse(TaskOutputChunkEntity chunk) {
        return new TaskOutputChunkResponse(
                chunk.getId(),
                chunk.getTaskId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getCreatedAt()
        );
    }
}
