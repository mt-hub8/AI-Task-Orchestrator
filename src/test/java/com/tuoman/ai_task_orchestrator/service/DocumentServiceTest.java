package com.tuoman.ai_task_orchestrator.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentServiceTest {

    private final DocumentService documentService = new DocumentService(null, null);

    @Test
    void shouldCreateOneChunkForShortText() {
        assertThat(documentService.splitIntoChunks("short text", 500))
                .containsExactly("short text");
    }

    @Test
    void shouldCreateMultipleChunksWhenTextExceedsChunkSize() {
        String content = "a".repeat(501);
        var chunks = documentService.splitIntoChunks(content, 500);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).hasSize(500);
    }

    @Test
    void shouldCreateNoChunkForBlankText() {
        assertThat(documentService.splitIntoChunks("   ", 500)).isEmpty();
    }
}
