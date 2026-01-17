package com.xunim.transcriptionapi.validator;

import com.xunim.transcriptionapi.exception.InvalidFileException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Component
@Slf4j
public class AudioFileValidator {

    @Value("${storage.max-file-size-mb:500}")
    private long maxFileSizeMb;

    // Tipos MIME permitidos
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "audio/mpeg",       // MP3
            "audio/mp3",        // MP3 (alternativo)
            "audio/mp4",        // M4A
            "audio/x-m4a",      // M4A (alternativo)
            "audio/m4a",        // M4A (alternativo)
            "audio/wav",        // WAV
            "audio/wave",       // WAV (alternativo)
            "audio/x-wav",      // WAV (alternativo)
            "audio/webm",       // WebM
            "audio/ogg",        // OGG
            "audio/flac",       // FLAC
            "audio/x-flac",     // FLAC (alternativo)
            "audio/aac"         // AAC
    );

    // Extensões permitidas
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".mp3", ".m4a", ".wav", ".webm", ".ogg", ".flac", ".aac"
    );

    // Magic numbers (primeiros bytes) de formatos de áudio
    private static final Map<String, byte[]> AUDIO_MAGIC_NUMBERS = Map.of(
            "MP3", new byte[]{(byte)0xFF, (byte)0xFB},              // MPEG-1 Layer 3
            "MP3_ID3", new byte[]{0x49, 0x44, 0x33},                // ID3 (MP3 com tags)
            "WAV", new byte[]{0x52, 0x49, 0x46, 0x46},              // RIFF (WAV)
            "M4A", new byte[]{0x00, 0x00, 0x00, 0x20, 0x66, 0x74},  // ftyp (M4A/MP4)
            "OGG", new byte[]{0x4F, 0x67, 0x67, 0x53},              // OggS
            "FLAC", new byte[]{0x66, 0x4C, 0x61, 0x43}              // fLaC
    );

    // Caracteres perigosos em nomes de arquivo
    private static final String DANGEROUS_CHARS_REGEX = "[^a-zA-Z0-9._-]";

    // Nomes de arquivo proibidos (Windows reserved names)
    private static final Set<String> FORBIDDEN_FILENAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    /**
     * Valida completamente um arquivo de áudio
     */
    public void validate(MultipartFile file) {
        validateNotEmpty(file);
        validateSize(file);
        validateFilename(file);
        validateExtension(file);
        validateMimeType(file);
        validateMagicNumbers(file);
    }

    /**
     * Valida se o arquivo não está vazio
     */
    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Nenhum arquivo foi enviado");
        }

        if (file.getSize() == 0) {
            throw new InvalidFileException("O arquivo está vazio");
        }
    }

    /**
     * Valida o tamanho do arquivo
     */
    private void validateSize(MultipartFile file) {
        long fileSizeMb = file.getSize() / (1024 * 1024);

        if (fileSizeMb > maxFileSizeMb) {
            throw new InvalidFileException(
                    String.format("Arquivo muito grande. Tamanho máximo: %d MB, tamanho enviado: %d MB",
                            maxFileSizeMb, fileSizeMb)
            );
        }

        // Validação adicional: arquivo muito pequeno (menos de 1KB) pode ser suspeito
        if (file.getSize() < 1024) {
            log.warn("Arquivo muito pequeno enviado: {} bytes", file.getSize());
        }
    }

    /**
     * Valida o nome do arquivo
     */
    private void validateFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();

        if (filename == null || filename.trim().isEmpty()) {
            throw new InvalidFileException("Nome do arquivo inválido");
        }

        // Remove caminho (segurança contra path traversal)
        String baseName = getBaseName(filename);

        // Valida caracteres perigosos
        if (containsDangerousCharacters(baseName)) {
            log.warn("Nome de arquivo com caracteres perigosos: {}", filename);
        }

        // Valida nomes reservados do Windows
        String nameWithoutExt = baseName.replaceFirst("\\.[^.]+$", "").toUpperCase();
        if (FORBIDDEN_FILENAMES.contains(nameWithoutExt)) {
            throw new InvalidFileException(
                    String.format("Nome de arquivo proibido: %s", baseName)
            );
        }

        // Valida tamanho do nome
        if (baseName.length() > 255) {
            throw new InvalidFileException("Nome do arquivo muito longo (máximo: 255 caracteres)");
        }

        // Valida múltiplas extensões (possível tentativa de bypass)
        if (countDots(baseName) > 1) {
            log.warn("Arquivo com múltiplas extensões: {}", baseName);
        }
    }

    /**
     * Valida a extensão do arquivo
     */
    private void validateExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();

        if (filename == null) {
            throw new InvalidFileException("Nome do arquivo não pode ser nulo");
        }

        String extension = getExtension(filename).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidFileException(
                    String.format("Extensão '%s' não é permitida. Extensões aceitas: %s",
                            extension, String.join(", ", ALLOWED_EXTENSIONS))
            );
        }
    }

    /**
     * Valida o tipo MIME
     */
    private void validateMimeType(MultipartFile file) {
        String mimeType = file.getContentType();

        if (mimeType == null) {
            log.warn("Tipo MIME não informado para arquivo: {}", file.getOriginalFilename());
            return; // Não falha, mas continua com outras validações
        }

        String normalizedMime = mimeType.toLowerCase().trim();

        if (!ALLOWED_MIME_TYPES.contains(normalizedMime)) {
            throw new InvalidFileException(
                    String.format("Tipo de arquivo não suportado: %s. Envie um arquivo de áudio válido.",
                            mimeType)
            );
        }
    }

    /**
     * Valida os magic numbers (assinatura do arquivo)
     * Esta é a validação mais confiável, pois verifica o conteúdo real do arquivo
     */
    private void validateMagicNumbers(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12]; // Lê os primeiros 12 bytes
            int bytesRead = is.read(header);

            if (bytesRead < 4) {
                throw new InvalidFileException("Arquivo corrompido ou muito pequeno");
            }

            boolean isValidAudio = false;

            for (Map.Entry<String, byte[]> entry : AUDIO_MAGIC_NUMBERS.entrySet()) {
                if (matchesMagicNumber(header, entry.getValue())) {
                    isValidAudio = true;
                    log.debug("Arquivo identificado como: {}", entry.getKey());
                    break;
                }
            }

            if (!isValidAudio) {
                log.error("Magic numbers inválidos. Header: {}", bytesToHex(header));
                throw new InvalidFileException(
                        "O arquivo não parece ser um áudio válido. Verifique se o arquivo não está corrompido."
                );
            }

        } catch (IOException e) {
            log.error("Erro ao ler arquivo para validação", e);
            throw new InvalidFileException("Não foi possível validar o arquivo", e);
        }
    }

    /**
     * Sanitiza o nome do arquivo, removendo caracteres perigosos
     */
    public String sanitizeFilename(String filename) {
        if (filename == null) {
            return "audio.mp3";
        }

        // Remove path (segurança)
        String baseName = getBaseName(filename);

        // Substitui caracteres perigosos por underscore
        String sanitized = baseName.replaceAll(DANGEROUS_CHARS_REGEX, "_");

        // Remove underscores duplicados
        sanitized = sanitized.replaceAll("_+", "_");

        // Remove underscores no início e fim
        sanitized = sanitized.replaceAll("^_+|_+$", "");

        // Se ficou vazio, usa nome padrão
        if (sanitized.isEmpty()) {
            return "audio" + getExtension(filename);
        }

        // Limita tamanho
        if (sanitized.length() > 200) {
            String ext = getExtension(sanitized);
            sanitized = sanitized.substring(0, 200 - ext.length()) + ext;
        }

        return sanitized;
    }

    // ==================== Métodos Auxiliares ====================

    private String getBaseName(String path) {
        if (path == null) return "";

        // Remove diretórios (Unix e Windows)
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private String getExtension(String filename) {
        if (filename == null) return ".mp3";

        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : "";
    }

    private boolean containsDangerousCharacters(String filename) {
        return !filename.matches("^[a-zA-Z0-9._-]+$");
    }

    private int countDots(String filename) {
        return (int) filename.chars().filter(ch -> ch == '.').count();
    }

    private boolean matchesMagicNumber(byte[] fileHeader, byte[] magicNumber) {
        if (fileHeader.length < magicNumber.length) {
            return false;
        }

        for (int i = 0; i < magicNumber.length; i++) {
            if (fileHeader[i] != magicNumber[i]) {
                return false;
            }
        }

        return true;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * Retorna informações sobre o arquivo validado (útil para debug)
     */
    public Map<String, Object> getFileInfo(MultipartFile file) {
        Map<String, Object> info = new HashMap<>();

        info.put("filename", file.getOriginalFilename());
        info.put("sanitizedFilename", sanitizeFilename(file.getOriginalFilename()));
        info.put("contentType", file.getContentType());
        info.put("size", file.getSize());
        info.put("sizeMB", file.getSize() / (1024.0 * 1024.0));

        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            is.read(header);
            info.put("magicNumbers", bytesToHex(header));
        } catch (IOException e) {
            info.put("magicNumbers", "Error reading");
        }

        return info;
    }
}