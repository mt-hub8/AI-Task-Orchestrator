package com.tuoman.ai_task_orchestrator.service;

import com.tuoman.ai_task_orchestrator.dto.DocumentChunkResponse;
import com.tuoman.ai_task_orchestrator.dto.DocumentDetailResponse;
import com.tuoman.ai_task_orchestrator.dto.DocumentUploadResponse;
import com.tuoman.ai_task_orchestrator.entity.DocumentChunkEntity;
import com.tuoman.ai_task_orchestrator.entity.DocumentEntity;
import com.tuoman.ai_task_orchestrator.enums.DocumentStatus;
import com.tuoman.ai_task_orchestrator.repository.DocumentChunkRepository;
import com.tuoman.ai_task_orchestrator.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int CHUNK_SIZE = 500;

    private final DocumentRepository documentRepository;

    private final DocumentChunkRepository documentChunkRepository;

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public DocumentUploadResponse uploadDocument(MultipartFile file) {
        validateFile(file);

        DocumentEntity document = new DocumentEntity();
        document.setOriginalFilename(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setStatus(DocumentStatus.UPLOADED);
        document.setChunkCount(0);

        DocumentEntity savedDocument = documentRepository.save(document);

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<String> chunks = splitIntoChunks(content, CHUNK_SIZE);
            List<DocumentChunkEntity> chunkEntities = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                String chunkContent = chunks.get(i);

                DocumentChunkEntity chunk = new DocumentChunkEntity();
                chunk.setDocumentId(savedDocument.getId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunkContent);
                chunk.setContentLength(chunkContent.length());
                chunkEntities.add(chunk);
            }

            documentChunkRepository.saveAll(chunkEntities);

            savedDocument.setStatus(DocumentStatus.CHUNKED);
            savedDocument.setChunkCount(chunkEntities.size());
            DocumentEntity chunkedDocument = documentRepository.save(savedDocument);

            return toUploadResponse(chunkedDocument);
        } catch (Exception e) {
            savedDocument.setStatus(DocumentStatus.FAILED);
            savedDocument.setErrorMessage(e.getMessage());
            documentRepository.save(savedDocument);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Document processing failed");
        }
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse getDocument(Long documentId) {
        DocumentEntity document = findDocumentOrThrow(documentId);
        return toDetailResponse(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentChunkResponse> getDocumentChunks(Long documentId) {
        findDocumentOrThrow(documentId);
        return documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId)
                .stream()
                .map(this::toChunkResponse)
                .toList();
    }

    List<String> splitIntoChunks(String content, int chunkSize) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();

        for (int start = 0; start < content.length(); start += chunkSize) {
            int end = Math.min(start + chunkSize, content.length());
            chunks.add(content.substring(start, end));
        }

        return chunks;
    }

    private void validateFile(MultipartFile file) {
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be null");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .txt and .md files are supported");
        }

        String lowerFilename = originalFilename.toLowerCase();
        if (!lowerFilename.endsWith(".txt") && !lowerFilename.endsWith(".md")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .txt and .md files are supported");
        }
    }

    private DocumentEntity findDocumentOrThrow(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private DocumentUploadResponse toUploadResponse(DocumentEntity document) {
        return new DocumentUploadResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getStatus().name(),
                document.getChunkCount()
        );
    }

    private DocumentDetailResponse toDetailResponse(DocumentEntity document) {
        return new DocumentDetailResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSize(),
                document.getStatus().name(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private DocumentChunkResponse toChunkResponse(DocumentChunkEntity chunk) {
        return new DocumentChunkResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getContentLength(),
                chunk.getCreatedAt()
        );
    }
}
