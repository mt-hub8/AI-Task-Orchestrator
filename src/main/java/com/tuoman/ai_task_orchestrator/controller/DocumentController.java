package com.tuoman.ai_task_orchestrator.controller;

import com.tuoman.ai_task_orchestrator.dto.DocumentChunkResponse;
import com.tuoman.ai_task_orchestrator.dto.DocumentDetailResponse;
import com.tuoman.ai_task_orchestrator.dto.DocumentUploadResponse;
import com.tuoman.ai_task_orchestrator.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public DocumentUploadResponse uploadDocument(@RequestParam("file") MultipartFile file) {
        return documentService.uploadDocument(file);
    }

    @GetMapping("/{documentId}")
    public DocumentDetailResponse getDocument(@PathVariable Long documentId) {
        return documentService.getDocument(documentId);
    }

    @GetMapping("/{documentId}/chunks")
    public List<DocumentChunkResponse> getDocumentChunks(@PathVariable Long documentId) {
        return documentService.getDocumentChunks(documentId);
    }
}
