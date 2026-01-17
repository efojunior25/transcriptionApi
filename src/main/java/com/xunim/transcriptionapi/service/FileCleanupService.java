package com.xunim.transcriptionapi.service;

import com.xunim.transcriptionapi.exception.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Service
@Slf4j
public class FileCleanupService {

    @Value("${storage.upload-dir:uploads}")
    private String uploadDir;

    public void deleteAudioFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }

        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("Arquivo deletado: {}", filePath);
            } else {
                log.warn("Arquivo não encontrado para deletar: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Erro ao deletar arquivo: {}", filePath, e);
            // Não lança exceção - limpeza é best effort
        }
    }

    public void deleteChunksDirectory(String originalFilePath) {
        if (originalFilePath == null || originalFilePath.isEmpty()) {
            return;
        }

        try {
            Path chunksDir = getChunksDirectory(originalFilePath);

            if (Files.exists(chunksDir) && Files.isDirectory(chunksDir)) {
                deleteDirectoryRecursively(chunksDir);
                log.info("Diretório de chunks deletado: {}", chunksDir);
            }
        } catch (IOException e) {
            log.error("Erro ao deletar diretório de chunks: {}", originalFilePath, e);
            // Não lança exceção - limpeza é best effort
        }
    }

    public void deleteFileAndChunks(String filePath) {
        deleteAudioFile(filePath);
        deleteChunksDirectory(filePath);
    }

    public Path getChunksDirectory(String originalFilePath) {
        Path originalFile = Paths.get(originalFilePath);
        String baseName = originalFile.getFileName().toString()
                .replaceFirst("\\.[^.]+$", "");
        return originalFile.getParent().resolve(baseName + "_chunks");
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Não foi possível deletar: {}", path, e);
                        }
                    });
        }
    }

    public void cleanOrphanedFiles() {
        try {
            Path uploadsPath = Paths.get(uploadDir);

            if (!Files.exists(uploadsPath)) {
                log.warn("Diretório de uploads não existe: {}", uploadDir);
                return;
            }

            try (Stream<Path> stream = Files.list(uploadsPath)) {
                stream.filter(Files::isDirectory)
                        .filter(dir -> dir.getFileName().toString().endsWith("_chunks"))
                        .forEach(chunksDir -> {
                            try {
                                deleteDirectoryRecursively(chunksDir);
                                log.info("Diretório órfão deletado: {}", chunksDir);
                            } catch (IOException e) {
                                log.error("Erro ao deletar diretório órfão: {}", chunksDir, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.error("Erro ao limpar arquivos órfãos", e);
            throw new FileStorageException("Erro ao limpar arquivos órfãos", e);
        }
    }

    public long calculateUploadsDirSize() {
        try {
            Path uploadsPath = Paths.get(uploadDir);

            if (!Files.exists(uploadsPath)) {
                return 0;
            }

            try (Stream<Path> walk = Files.walk(uploadsPath)) {
                return walk.filter(Files::isRegularFile)
                        .mapToLong(path -> {
                            try {
                                return Files.size(path);
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .sum();
            }
        } catch (IOException e) {
            log.error("Erro ao calcular tamanho do diretório", e);
            return 0;
        }
    }
}