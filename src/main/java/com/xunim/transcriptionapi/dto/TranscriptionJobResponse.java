package com.xunim.transcriptionapi.dto;

import com.xunim.transcriptionapi.model.TranscriptionJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionJobResponse {

    private Long id;
    private String fileName;
    private TranscriptionJob.Status status;
    private String transcriptionText;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    public static TranscriptionJobResponse fromEntity(TranscriptionJob job) {
        return TranscriptionJobResponse.builder()
                .id(job.getId())
                .fileName(job.getFileName())
                .status(job.getStatus())
                .transcriptionText(job.getTranscriptionText())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .build();
    }
}