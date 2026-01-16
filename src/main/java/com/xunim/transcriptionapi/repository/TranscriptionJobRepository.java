package com.xunim.transcriptionapi.repository;

import com.xunim.transcriptionapi.model.TranscriptionJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptionJobRepository extends JpaRepository<TranscriptionJob, Long> {
}
