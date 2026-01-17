package com.xunim.transcriptionapi.service;

import com.xunim.transcriptionapi.async.TranscriptionAsyncProcessor;
import com.xunim.transcriptionapi.dto.JobStatistics;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Serviço responsável pela lógica de negócio das transcrições
 *
 * Esta versão usa JobStatistics como classe separada em dto/JobStatistics.java
 */
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

    @Transactional
    public TranscriptionJobResponse createJob(MultipartFile file, String language, Integer maxSegmentSeconds) {

        audioFileValidator.validate(file);

        String originalFilename = file.getOriginalFilename();
        String sanitizedFilename = audioFileValidator.sanitizeFilename(originalFilename);
        String fileExtension = getExtension(sanitizedFilename);

        String uniqueFilename = System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8) +
                fileExtension;
        Path path = Paths.get(uploadDir, uniqueFilename);

        try {
            Files.createDirectories(path.getParent());
            Files.write(path, file.getBytes());

            log.info("Arquivo salvo: {} (original: {})", path, originalFilename);

        } catch (IOException e) {
            log.error("Erro ao salvar arquivo: {}", path, e);
            throw new FileStorageException("Não foi possível salvar o arquivo", e);
        }

        String normalizedLanguage = normalizeLanguage(language);

        TranscriptionJob job = TranscriptionJob.builder()
                .fileName(sanitizedFilename)
                .filePath(path.toString())
                .status(TranscriptionJob.Status.UPLOADED)
                .language(normalizedLanguage)
                .fileSizeBytes(file.getSize())
                .build();

        job = repository.save(job);

        log.info("Job {} criado para arquivo: {} ({} MB)",
                job.getId(), sanitizedFilename, file.getSize() / (1024 * 1024));

        final Long jobId = job.getId();
        processor.process(jobId, maxSegmentSeconds);

        return TranscriptionJobResponse.fromEntity(job);
    }

    @Transactional(readOnly = true)
    public TranscriptionJobResponse findById(Long id) {
        TranscriptionJob job = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de transcrição não encontrado com ID: " + id
                ));

        return TranscriptionJobResponse.fromEntity(job);
    }

    @Transactional
    public void deleteJob(Long id) {
        TranscriptionJob job = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de transcrição não encontrado com ID: " + id
                ));

        try {
            repository.delete(job);
            fileCleanupService.deleteFileAndChunks(job.getFilePath());
            log.info("Job {} deletado completamente", id);

        } catch (Exception e) {
            log.error("Erro ao deletar job {}", id, e);
            throw new FileStorageException("Erro ao deletar job e seus arquivos", e);
        }
    }

    @Transactional
    public TranscriptionJobResponse retryJob(Long id, Integer maxSegmentSeconds) {
        TranscriptionJob job = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job de transcrição não encontrado com ID: " + id
                ));

        if (job.getStatus() == TranscriptionJob.Status.PROCESSING) {
            throw new IllegalStateException(
                    "Job ainda está em processamento. Aguarde a conclusão antes de tentar novamente."
            );
        }

        if (job.getStatus() == TranscriptionJob.Status.DONE) {
            throw new IllegalStateException(
                    "Job já foi concluído com sucesso. Não é necessário reprocessar."
            );
        }

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

        job = repository.save(job);

        log.info("Reprocessando job {}", id);

        final Long jobId = job.getId();
        processor.process(jobId, maxSegmentSeconds);

        return TranscriptionJobResponse.fromEntity(job);
    }

    /**
     * Retorna estatísticas gerais do sistema
     */
    @Transactional(readOnly = true)
    public JobStatistics getStatistics() {
        long total = repository.count();
        long processing = repository.countByStatus(TranscriptionJob.Status.PROCESSING);
        long completed = repository.countByStatus(TranscriptionJob.Status.DONE);
        long errors = repository.countByStatus(TranscriptionJob.Status.ERROR);

        return JobStatistics.builder()
                .total(total)
                .processing(processing)
                .completed(completed)
                .errors(errors)
                .build();
    }

    private String getExtension(String filename) {
        if (filename == null) return ".mp3";
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : ".mp3";
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            return null;
        }

        String normalized = language.trim().toLowerCase();

        if (normalized.length() < 2 || normalized.length() > 3) {
            log.warn("Idioma fornecido parece inválido: '{}'. Será ignorado.", language);
            return null;
        }

        if (!normalized.matches("^[a-z]{2,3}$")) {
            log.warn("Idioma com formato inválido: '{}'. Esperado: 2-3 letras (ex: pt, en, es)", language);
            return null;
        }

        return normalized;
    }
}