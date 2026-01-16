package com.xunim.transcriptionapi.service;

import com.xunim.transcriptionapi.async.TranscriptionAsyncProcessor;
import com.xunim.transcriptionapi.model.TranscriptionJob;
import com.xunim.transcriptionapi.repository.TranscriptionJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TranscriptionService {

    private final TranscriptionJobRepository repository;
    private final TranscriptionAsyncProcessor processor;

    public TranscriptionJob createJob(MultipartFile file) throws IOException {

        if (!"audio/mpeg".equals(file.getContentType())) {
            throw new IllegalArgumentException("Arquivo deve ser MP3");
        }

        Path path = Paths.get("uploads/" + UUID.randomUUID() + ".mp3");
        Files.createDirectories(path.getParent());
        Files.write(path, file.getBytes());

        TranscriptionJob job = TranscriptionJob.builder()
                .fileName(file.getOriginalFilename())
                .filePath(path.toString())
                .status(TranscriptionJob.Status.UPLOADED)
                .build();

        repository.save(job);
        processor.process(job.getId());

        return job;
    }

    public TranscriptionJob findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job não encontrado"));
    }
}

