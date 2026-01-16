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
    private String filePath;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String transcriptionText;

    private LocalDateTime createdAt;

    public enum Status {
        UPLOADED, PROCESSING, DONE, ERROR
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
