package com.xunim.transcriptionapi.async;

import com.xunim.transcriptionapi.ai.WhisperClient;
import com.xunim.transcriptionapi.model.TranscriptionJob;
import com.xunim.transcriptionapi.repository.TranscriptionJobRepository;
import com.xunim.transcriptionapi.service.AudioSplitService;
import com.xunim.transcriptionapi.service.FileCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TranscriptionAsyncProcessor {

    private final TranscriptionJobRepository repository;
    private final WhisperClient whisperClient;
    private final AudioSplitService audioSplitService;
    private final FileCleanupService fileCleanupService;

    @Async
    public void process(Long jobId, Integer maxSegmentSeconds) {

        TranscriptionJob job = repository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job não encontrado: " + jobId));

        try {
            log.info("Iniciando processamento do job {}", jobId);

            job.markAsProcessing();
            repository.save(job);

            int segmentSeconds = maxSegmentSeconds != null ? maxSegmentSeconds : 600;
            List<Path> chunks = audioSplitService.splitAudio(job.getFilePath(), segmentSeconds);

            StringBuilder finalText = new StringBuilder();

            for (int i = 0; i < chunks.size(); i++) {
                Path chunk = chunks.get(i);
                log.info("Transcrevendo chunk {}/{} do job {}", i + 1, chunks.size(), jobId);

                String partial = whisperClient.transcribe(chunk.toString(), job.getLanguage());
                finalText.append(partial).append("\n");
            }

            job.markAsCompleted(finalText.toString());
            log.info("Job {} concluído com sucesso", jobId);

            // IMPORTANTE: Limpa os chunks após conclusão bem-sucedida
            cleanupChunksAfterCompletion(job);

        } catch (Exception e) {
            log.error("Erro ao processar job {}", jobId, e);
            job.markAsError(e.getMessage());

            // Limpa chunks mesmo em caso de erro
            cleanupChunksAfterError(job);

        } finally {
            repository.save(job);
        }
    }

    /**
     * Limpa chunks após conclusão bem-sucedida
     * Mantém apenas o arquivo original e a transcrição no banco
     */
    private void cleanupChunksAfterCompletion(TranscriptionJob job) {
        try {
            log.info("Limpando chunks do job {} após conclusão", job.getId());
            fileCleanupService.deleteChunksDirectory(job.getFilePath());
        } catch (Exception e) {
            log.error("Erro ao limpar chunks do job {}", job.getId(), e);
            // Não falha o job se a limpeza falhar
        }
    }

    /**
     * Limpa chunks após erro
     * Mantém o arquivo original para possível reprocessamento
     */
    private void cleanupChunksAfterError(TranscriptionJob job) {
        try {
            log.info("Limpando chunks do job {} após erro", job.getId());
            fileCleanupService.deleteChunksDirectory(job.getFilePath());
        } catch (Exception e) {
            log.error("Erro ao limpar chunks do job {} após falha", job.getId(), e);
        }
    }
}