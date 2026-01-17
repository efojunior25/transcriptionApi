package com.xunim.transcriptionapi.controller;

import com.xunim.transcriptionapi.dto.TranscriptionJobResponse;
import com.xunim.transcriptionapi.service.TranscriptionService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/transcriptions")
@RequiredArgsConstructor
public class TranscriptionController {

    private final TranscriptionService service;

    // Injetando a chave da aplicação
    @Value("${api.security.api-key}")
    private String apiKey;

    @PostConstruct
    public void logApiKey() {
        // Imprime a chave carregada pelo Spring Boot ao iniciar
        System.out.println("Chave carregada pelo Spring: " + apiKey);
    }

    @PostMapping
    public ResponseEntity<TranscriptionJobResponse> upload(
            @RequestHeader(value = "X-API-Key", required = false) String headerKey,
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "maxSegmentSeconds", required = false) Integer maxSegmentSeconds)
            throws IOException {

        // Imprime a chave recebida no header
        System.out.println("Header recebido: " + headerKey);
        System.out.println("Chave configurada no Spring: " + apiKey);

        // Teste rápido de validação (não obrigatório, mas útil para debug)
        if (headerKey == null || !headerKey.equals(apiKey)) {
            System.out.println("Chave inválida! Retornando 403");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        TranscriptionJobResponse response = service.createJob(file, language, maxSegmentSeconds);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TranscriptionJobResponse> status(@PathVariable Long id) {
        TranscriptionJobResponse response = service.findById(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteJob(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<TranscriptionJobResponse> retry(
            @PathVariable Long id,
            @RequestParam(value = "maxSegmentSeconds", required = false) Integer maxSegmentSeconds) {

        TranscriptionJobResponse response = service.retryJob(id, maxSegmentSeconds);
        return ResponseEntity.ok(response);
    }
}