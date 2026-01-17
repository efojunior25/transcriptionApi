package com.xunim.transcriptionapi.controller;

import com.xunim.transcriptionapi.dto.JobStatistics;
import com.xunim.transcriptionapi.dto.TranscriptionJobResponse;
import com.xunim.transcriptionapi.service.TranscriptionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller para gerenciar transcrições de áudio
 *
 * ✅ CORREÇÕES APLICADAS (VERSÃO FINAL):
 * - ❌ REMOVIDO: Injeção de api.security.api-key (não é responsabilidade do controller!)
 * - ❌ REMOVIDO: @PostConstruct com System.out.println
 * - ❌ REMOVIDO: @RequestHeader("X-API-Key") - autenticação é feita no Filter
 * - ❌ REMOVIDO: Validação manual de API Key - já é feita no ApiKeyAuthFilter
 * - ✅ ADICIONADO: @Validated para validação automática de parâmetros
 * - ✅ ADICIONADO: Logging apropriado com SLF4J
 * - ✅ ADICIONADO: Validação de range nos parâmetros
 */
@RestController
@RequestMapping("/api/transcriptions")
@RequiredArgsConstructor
@Slf4j
@Validated
public class TranscriptionController {

    private final TranscriptionService service;

    /**
     * Cria um novo job de transcrição de áudio
     *
     * Endpoint: POST /api/transcriptions
     * Header obrigatório: X-API-Key (validado automaticamente pelo ApiKeyAuthFilter)
     *
     * @param file Arquivo de áudio (formatos suportados: MP3, M4A, WAV, WEBM, OGG, FLAC, AAC)
     * @param language Código ISO do idioma (opcional): pt, en, es, fr, etc. Se omitido, Whisper detecta automaticamente
     * @param maxSegmentSeconds Tamanho máximo de cada chunk em segundos (padrão: 600, min: 60, max: 3600)
     * @return Job criado com ID para acompanhamento do status
     *
     * @throws IOException Se houver erro ao processar o arquivo
     */
    @PostMapping
    public ResponseEntity<TranscriptionJobResponse> upload(
            @RequestParam("file")
            @NotNull(message = "Arquivo de áudio é obrigatório")
            MultipartFile file,

            @RequestParam(value = "language", required = false)
            String language,

            @RequestParam(value = "maxSegmentSeconds", required = false)
            @Min(value = 60, message = "Segmento deve ter no mínimo 60 segundos")
            @Max(value = 3600, message = "Segmento deve ter no máximo 3600 segundos (1 hora)")
            Integer maxSegmentSeconds) throws IOException {

        log.info("📥 Upload recebido: arquivo='{}', tamanho={} MB, idioma={}",
                file.getOriginalFilename(),
                String.format("%.2f", file.getSize() / (1024.0 * 1024.0)),
                language != null ? language : "auto-detect");

        TranscriptionJobResponse response = service.createJob(file, language, maxSegmentSeconds);

        log.info("✅ Job {} criado com sucesso para arquivo '{}'",
                response.getId(), file.getOriginalFilename());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Consulta o status de um job de transcrição
     *
     * Endpoint: GET /api/transcriptions/{id}
     *
     * @param id ID do job de transcrição
     * @return Informações do job incluindo status, texto transcrito (se concluído) e possíveis erros
     */
    @GetMapping("/{id}")
    public ResponseEntity<TranscriptionJobResponse> status(@PathVariable Long id) {
        log.debug("🔍 Consultando status do job {}", id);

        TranscriptionJobResponse response = service.findById(id);

        log.debug("📊 Job {}: status={}, progresso={}",
                id, response.getStatus(),
                response.getTranscriptionText() != null ? "concluído" : "em andamento");

        return ResponseEntity.ok(response);
    }

    /**
     * Deleta um job e todos seus arquivos associados (original + chunks)
     *
     * Endpoint: DELETE /api/transcriptions/{id}
     *
     * @param id ID do job a ser deletado
     * @return 204 No Content em caso de sucesso
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("🗑️ Requisição de deleção do job {}", id);

        service.deleteJob(id);

        log.info("✅ Job {} deletado com sucesso", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reprocessa um job que teve erro
     *
     * Endpoint: POST /api/transcriptions/{id}/retry
     *
     * @param id ID do job com erro a ser reprocessado
     * @param maxSegmentSeconds (Opcional) Novo tamanho de segmento, se quiser tentar com configuração diferente
     * @return Job atualizado com novo status
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<TranscriptionJobResponse> retry(
            @PathVariable Long id,
            @RequestParam(value = "maxSegmentSeconds", required = false)
            @Min(value = 60, message = "Segmento deve ter no mínimo 60 segundos")
            @Max(value = 3600, message = "Segmento deve ter no máximo 3600 segundos")
            Integer maxSegmentSeconds) {

        log.info("🔄 Tentando reprocessar job {}", id);

        TranscriptionJobResponse response = service.retryJob(id, maxSegmentSeconds);

        log.info("✅ Job {} reprocessamento iniciado", id);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtém estatísticas gerais do sistema
     *
     * Endpoint: GET /api/transcriptions/stats
     *
     * @return Estatísticas: total de jobs, jobs em processamento, concluídos, com erro e taxa de sucesso
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.debug("📊 Consultando estatísticas do sistema");

        JobStatistics stats = service.getStatistics();

        Map<String, Object> response = new HashMap<>();
        response.put("total", stats.getTotal());
        response.put("processing", stats.getProcessing());
        response.put("completed", stats.getCompleted());
        response.put("errors", stats.getErrors());
        response.put("success_rate_percent", stats.getSuccessRate());
        response.put("error_rate_percent", stats.getErrorRate());
        response.put("has_jobs_processing", stats.hasJobsProcessing());

        return ResponseEntity.ok(response);
    }

    /**
     * Health check específico da API de transcrição
     *
     * Endpoint: GET /api/transcriptions/health
     *
     * @return Status UP se tudo OK
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "transcription-api");
        health.put("version", "1.0.0");

        return ResponseEntity.ok(health);
    }

}