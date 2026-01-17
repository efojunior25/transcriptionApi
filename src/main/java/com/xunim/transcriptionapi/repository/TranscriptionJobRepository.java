package com.xunim.transcriptionapi.repository;

import com.xunim.transcriptionapi.model.TranscriptionJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TranscriptionJobRepository extends JpaRepository<TranscriptionJob, Long> {

    // Busca jobs criados antes de uma data
    List<TranscriptionJob> findByCreatedAtBefore(LocalDateTime date);

    // Busca jobs por status
    List<TranscriptionJob> findByStatus(TranscriptionJob.Status status);

    // Busca jobs por status e criados antes de uma data
    List<TranscriptionJob> findByStatusAndCreatedAtBefore(
            TranscriptionJob.Status status,
            LocalDateTime date
    );

    // Conta jobs por status
    long countByStatus(TranscriptionJob.Status status);

}