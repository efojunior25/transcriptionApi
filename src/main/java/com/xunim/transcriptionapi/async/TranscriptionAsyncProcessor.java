package com.xunim.transcriptionapi.async;

import com.xunim.transcriptionapi.ai.WhisperClient;
import com.xunim.transcriptionapi.model.TranscriptionJob;
import com.xunim.transcriptionapi.repository.TranscriptionJobRepository;
import com.xunim.transcriptionapi.service.AudioSplitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private AudioSplitService audioSplitService;

    @Async
    public void process(Long jobId) {

        TranscriptionJob job = repository.findById(jobId).orElseThrow();

        try {
            job.setStatus(TranscriptionJob.Status.PROCESSING);
            repository.save(job);

            List<Path> chunks =
                    audioSplitService.splitAudio(job.getFilePath(), 600); // 10 minutos

            StringBuilder finalText = new StringBuilder();

            for (Path chunk : chunks) {
                String partial = whisperClient.transcribe(chunk.toString());
                finalText.append(partial).append("\n");
            }

            job.setTranscriptionText(finalText.toString());
            job.setStatus(TranscriptionJob.Status.DONE);

        } catch (Exception e) {
            log.error("Erro ao processar job {}", jobId, e);
            job.setStatus(TranscriptionJob.Status.ERROR);
        }

        repository.save(job);
    }
}

