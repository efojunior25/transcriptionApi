package com.xunim.transcriptionapi.exception;

import com.xunim.transcriptionapi.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Tratamento para recurso não encontrado
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Recurso não encontrado [{}]: {}", traceId, ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "Recurso Não Encontrado",
                ex.getMessage(),
                request.getRequestURI()
        );
        error.setTraceId(traceId);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Tratamento para arquivo inválido
     */
    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFile(
            InvalidFileException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Arquivo inválido [{}]: {}", traceId, ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Arquivo Inválido",
                ex.getMessage(),
                request.getRequestURI()
        );
        error.setTraceId(traceId);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Tratamento para erros de armazenamento
     */
    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ErrorResponse> handleFileStorage(
            FileStorageException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Erro de armazenamento [{}]: {}", traceId, ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Erro de Armazenamento",
                "Não foi possível salvar o arquivo. Tente novamente.",
                request.getRequestURI()
        );
        error.setTraceId(traceId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Tratamento para erros de processamento de áudio
     */
    @ExceptionHandler(AudioProcessingException.class)
    public ResponseEntity<ErrorResponse> handleAudioProcessing(
            AudioProcessingException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Erro no processamento de áudio [{}]: {}", traceId, ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Erro no Processamento",
                "Não foi possível processar o áudio. Verifique se o arquivo está corrompido.",
                request.getRequestURI()
        );
        error.setTraceId(traceId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Tratamento para erros da API Whisper
     */
    @ExceptionHandler(WhisperApiException.class)
    public ResponseEntity<ErrorResponse> handleWhisperApi(
            WhisperApiException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Erro na API Whisper [{}]: {} (Status: {})",
                traceId, ex.getMessage(), ex.getStatusCode(), ex);

        String userMessage = getUserFriendlyWhisperError(ex.getStatusCode());

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_GATEWAY.value(),
                "Erro na Transcrição",
                userMessage,
                request.getRequestURI()
        );
        error.setTraceId(traceId);

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    /**
     * Tratamento para erro de transcrição genérico
     */
    @ExceptionHandler(TranscriptionException.class)
    public ResponseEntity<ErrorResponse> handleTranscription(
            TranscriptionException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Erro na transcrição [{}]: {}", traceId, ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Erro na Transcrição",
                "Ocorreu um erro durante a transcrição. Tente novamente mais tarde.",
                request.getRequestURI()
        );
        error.setTraceId(traceId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Tratamento para arquivo muito grande
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Arquivo muito grande [{}]", traceId);

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "Arquivo Muito Grande",
                "O arquivo enviado excede o tamanho máximo permitido.",
                request.getRequestURI()
        );
        error.setTraceId(traceId);

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    /**
     * Tratamento para parâmetros de requisição faltando
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Parâmetro obrigatório faltando [{}]: {}", traceId, ex.getParameterName());

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Parâmetro Obrigatório",
                String.format("O parâmetro '%s' é obrigatório", ex.getParameterName()),
                request.getRequestURI()
        );
        error.setTraceId(traceId);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Tratamento para arquivo multipart faltando
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            MissingServletRequestPartException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Arquivo obrigatório faltando [{}]: {}", traceId, ex.getRequestPartName());

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Arquivo Obrigatório",
                "É necessário enviar um arquivo de áudio",
                request.getRequestURI()
        );
        error.setTraceId(traceId);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Tratamento para erros de validação
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();

        List<ErrorResponse.ValidationError> validationErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::mapFieldError)
                .collect(Collectors.toList());

        log.warn("Erro de validação [{}]: {} campo(s) inválido(s)", traceId, validationErrors.size());

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de Validação",
                "Um ou mais campos estão inválidos",
                request.getRequestURI()
        );
        error.setTraceId(traceId);
        error.setValidationErrors(validationErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Tratamento para IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Argumento inválido [{}]: {}", traceId, ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "Argumento Inválido",
                ex.getMessage(),
                request.getRequestURI()
        );
        error.setTraceId(traceId);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Tratamento para IllegalStateException
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Estado inválido [{}]: {}", traceId, ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "Operação Inválida",
                ex.getMessage(),
                request.getRequestURI()
        );
        error.setTraceId(traceId);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Tratamento para todas as outras exceções não previstas
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Erro inesperado [{}]: {}", traceId, ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Erro Interno",
                "Ocorreu um erro inesperado. Nossa equipe foi notificada.",
                request.getRequestURI()
        );
        error.setTraceId(traceId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    // ==================== Métodos Auxiliares ====================

    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private ErrorResponse.ValidationError mapFieldError(FieldError fieldError) {
        return ErrorResponse.ValidationError.builder()
                .field(fieldError.getField())
                .message(fieldError.getDefaultMessage())
                .rejectedValue(fieldError.getRejectedValue())
                .build();
    }

    private String getUserFriendlyWhisperError(int statusCode) {
        return switch (statusCode) {
            case 400 -> "O arquivo de áudio está em formato inválido ou corrompido.";
            case 401 -> "Erro de autenticação com o serviço de transcrição.";
            case 413 -> "O arquivo de áudio é muito grande para ser processado.";
            case 429 -> "Limite de requisições excedido. Tente novamente em alguns minutos.";
            case 500, 502, 503 -> "O serviço de transcrição está temporariamente indisponível.";
            default -> "Não foi possível transcrever o áudio. Tente novamente.";
        };
    }
}