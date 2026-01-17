package com.xunim.transcriptionapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionJobRequest {

    @NotNull(message = "O arquivo é obrigatório")
    private MultipartFile file;

    private String language; // opcional: idioma do áudio (pt, en, es, etc.)

    private Integer maxSegmentSeconds; // opcional: override do tamanho dos chunks
}