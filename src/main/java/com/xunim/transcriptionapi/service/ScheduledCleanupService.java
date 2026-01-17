package com.xunim.transcriptionapi.service;

import com.xunim.transcriptionapi.model.TranscriptionJob;
import com.xunim.transcriptionapi.repository.TranscriptionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledCleanupService {

    private final TranscriptionJobRepository repository;
    private final FileCleanupService fileCleanupService;

    @Value("${storage.retention-days:7}")
    private int retentionDays;

    @Value("${storage.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    /**
     * Executa limpeza automática diariamente às 3h da manhã
     */
    @Scheduled(cron = "${storage.cleanup.cron:0 0 3 * * ?}")
    @Transactional
    public void cleanOldJobs() {
        if (!cleanupEnabled) {
            log.info("Limpeza automática está desabilitada");
            return;
        }

        log.info("Iniciando limpeza automática de jobs antigos (retenção: {} dias)", retentionDays);

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        List<TranscriptionJob> oldJobs = repository.findByCreatedAtBefore(cutoffDate);

        int deletedCount = 0;
        long freedSpace = 0;

        for (TranscriptionJob job : oldJobs) {
            try {
                // Calcula espaço antes de deletar
                long fileSize = job.getFileSizeBytes() != null ? job.getFileSizeBytes() : 0;

                // Deleta arquivos físicos
                fileCleanupService.deleteFileAndChunks(job.getFilePath());

                // Deleta do banco
                repository.delete(job);

                deletedCount++;
                freedSpace += fileSize;

                log.debug("Job {} deletado (criado em: {})", job.getId(), job.getCreatedAt());
            } catch (Exception e) {
                log.error("Erro ao deletar job {}", job.getId(), e);
            }
        }

        log.info("Limpeza concluída: {} jobs deletados, ~{} MB liberados",
                deletedCount, freedSpace / (1024 * 1024));
    }

    /**
     * Limpa chunks órfãos a cada 6 horas
     */
    @Scheduled(cron = "${storage.cleanup.orphans-cron:0 0 */6 * * ?}")
    public void cleanOrphanedChunks() {
        if (!cleanupEnabled) {
            return;
        }

        log.info("Iniciando limpeza de chunks órfãos");
        fileCleanupService.cleanOrphanedFiles();
    }

    /**
     * Limpa chunks de jobs concluídos a cada hora
     * (mantém apenas o arquivo original e a transcrição)
     */
    @Scheduled(cron = "${storage.cleanup.completed-chunks-cron:0 0 * * * ?}")
    @Transactional(readOnly = true)
    public void cleanCompletedJobChunks() {
        if (!cleanupEnabled) {
            return;
        }

        log.info("Iniciando limpeza de chunks de jobs concluídos");

        List<TranscriptionJob> completedJobs = repository.findByStatus(TranscriptionJob.Status.DONE);

        int cleanedCount = 0;
        for (TranscriptionJob job : completedJobs) {
            try {
                fileCleanupService.deleteChunksDirectory(job.getFilePath());
                cleanedCount++;
            } catch (Exception e) {
                log.error("Erro ao limpar chunks do job {}", job.getId(), e);
            }
        }

        log.info("Chunks de {} jobs concluídos foram limpos", cleanedCount);
    }

    /**
     * Gera relatório de uso de espaço (executa diariamente às 8h)
     */
    @Scheduled(cron = "${storage.report.cron:0 0 8 * * ?}")
    public void generateStorageReport() {
        if (!cleanupEnabled) {
            return;
        }

        long totalSize = fileCleanupService.calculateUploadsDirSize();
        long totalJobs = repository.count();
        long processingJobs = repository.countByStatus(TranscriptionJob.Status.PROCESSING);
        long completedJobs = repository.countByStatus(TranscriptionJob.Status.DONE);
        long errorJobs = repository.countByStatus(TranscriptionJob.Status.ERROR);

        log.info("=== RELATÓRIO DE ARMAZENAMENTO ===");
        log.info("Espaço total usado: {} MB", totalSize / (1024 * 1024));
        log.info("Total de jobs: {}", totalJobs);
        log.info("Jobs em processamento: {}", processingJobs);
        log.info("Jobs concluídos: {}", completedJobs);
        log.info("Jobs com erro: {}", errorJobs);
        log.info("==================================");
    }

    /**
     * Limpa jobs com erro há mais de N dias
     */
    @Scheduled(cron = "${storage.cleanup.error-jobs-cron:0 0 2 * * ?}")
    @Transactional
    public void cleanOldErrorJobs() {
        if (!cleanupEnabled) {
            return;
        }

        int errorRetentionDays = 3; // Jobs com erro mantidos por menos tempo
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(errorRetentionDays);

        List<TranscriptionJob> errorJobs = repository.findByStatusAndCreatedAtBefore(
                TranscriptionJob.Status.ERROR, cutoffDate
        );

        int deletedCount = 0;
        for (TranscriptionJob job : errorJobs) {
            try {
                fileCleanupService.deleteFileAndChunks(job.getFilePath());
                repository.delete(job);
                deletedCount++;
            } catch (Exception e) {
                log.error("Erro ao deletar job com erro {}", job.getId(), e);
            }
        }

        log.info("Jobs com erro antigos deletados: {}", deletedCount);
    }
}