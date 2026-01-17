package com.xunim.transcriptionapi.service;

import com.xunim.transcriptionapi.async.TranscriptionAsyncProcessor;
import com.xunim.transcriptionapi.dto.TranscriptionJobResponse;
import com.xunim.transcriptionapi.exception.FileStorageException;
import com.xunim.transcriptionapi.exception.ResourceNotFoundException;
import com.xunim.transcriptionapi.model.TranscriptionJob;
import com.xunim.transcriptionapi.repository.TranscriptionJobRepository;
import com.xunim.transcriptionapi.validator.AudioFileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranscriptionService {

    private final TranscriptionJobRepository repository;
    private final TranscriptionAsyncProcessor processor;
    private final FileCleanupService fileCleanupService;
    private final AudioFileValidator audioFileValidator;

    @Value("${storage.upload-dir:uploads}")
    private String uploadDir;

    public TranscriptionJobResponse createJob(MultipartFile file, String language, Integer maxSegmentSeconds) {

        // Valida o arquivo completamente
        audioFileValidator.validate(file);

        String originalFilename = file.getOriginalFilename();
        String sanitizedFilename = audioFileValidator.sanitizeFilename(originalFilename);
        String fileExtension = getExtension(sanitizedFilename);

        Path path = Paths.get(uploadDir, UUID.randomUUID() + fileExtension);

        try {
            Files.createDirectories(path.getParent());
            Files.write(path, file.getBytes());

            log.info("Arquivo salvo: {} (original: {})", path, originalFilename);

        } catch (IOException e) {
            log.error("Erro ao salvar arquivo: {}", path, e);
            throw new FileStorageException("Não foi possível salvar o arquivo", e);
        }

        TranscriptionJob job = TranscriptionJob.builder()
                .fileName(sanitizedFilename)
                .filePath(path.toString())
                .status(TranscriptionJob.Status.UPLOADED)
                .language(language)
                .fileSizeBytes(file.getSize())
                .build();

        repository.save(job);

        log.info("Job {} criado para arquivo: {} ({} MB)",
                job.getId(), sanitizedFilename, file.getSize() / (1024 * 1024));

        processor.process(job.getId(), maxSegmentSeconds);

        return TranscriptionJobResponse.fromEntity(job);
    }

    public TranscriptionJobResponse findById(Long id) {
        TranscriptionJob job = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de transcrição não encontrado com ID: " + id
                ));

        return TranscriptionJobResponse.fromEntity(job);
    }

    public void deleteJob(Long id) {
        TranscriptionJob job = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de transcrição não encontrado com ID: " + id
                ));

        fileCleanupService.deleteFileAndChunks(job.getFilePath());
        repository.delete(job);

        log.info("Job {} deletado completamente", id);
    }

    public TranscriptionJobResponse retryJob(Long id, Integer maxSegmentSeconds) {
        TranscriptionJob job = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de transcrição não encontrado com ID: " + id
                ));

        if (job.getStatus() != TranscriptionJob.Status.ERROR) {
            throw new IllegalStateException(
                    "Apenas jobs com erro podem ser reprocessados. Status atual: " + job.getStatus()
            );
        }

        fileCleanupService.deleteChunksDirectory(job.getFilePath());

        job.setStatus(TranscriptionJob.Status.UPLOADED);
        job.setErrorMessage(null);
        job.setTranscriptionText(null);
        job.setCompletedAt(null);
        repository.save(job);

        log.info("Reprocessando job {}", id);
        processor.process(job.getId(), maxSegmentSeconds);

        return TranscriptionJobResponse.fromEntity(job);
    }

    private String getExtension(String filename) {
        if (filename == null) return ".mp3";
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : ".mp3";
    }
}