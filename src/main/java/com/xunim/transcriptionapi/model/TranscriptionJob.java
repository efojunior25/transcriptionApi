package com.xunim.transcriptionapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "transcription_job")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;

    // Não expor no DTO - apenas uso interno
    private String filePath;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String transcriptionText;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    // Armazenar mensagem de erro caso falhe
    @Column(length = 1000)
    private String errorMessage;

    // Idioma da transcrição (opcional)
    private String language;

    // Tamanho original do arquivo em bytes
    private Long fileSizeBytes;

    public enum Status {
        UPLOADED, PROCESSING, DONE, ERROR
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = Status.UPLOADED;
        }
    }

    // Métodos de negócio
    public boolean isCompleted() {
        return status == Status.DONE;
    }

    public boolean hasError() {
        return status == Status.ERROR;
    }

    public boolean isProcessing() {
        return status == Status.PROCESSING;
    }

    public void markAsProcessing() {
        this.status = Status.PROCESSING;
    }

    public void markAsCompleted(String transcription) {
        this.status = Status.DONE;
        this.transcriptionText = transcription;
        this.completedAt = LocalDateTime.now();
    }

    public void markAsError(String errorMsg) {
        this.status = Status.ERROR;
        this.errorMessage = errorMsg;
        this.completedAt = LocalDateTime.now();
    }
}