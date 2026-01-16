package com.xunim.transcriptionapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AudioSplitService {

    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    public List<Path> splitAudio(String inputFilePath, int segmentSeconds) {

        try {
            File inputFile = new File(inputFilePath);

            if (!inputFile.exists()) {
                throw new IllegalArgumentException("Arquivo de áudio não encontrado");
            }

            String baseName = inputFile.getName().replaceFirst("\\.[^.]+$", "");
            Path outputDir = inputFile.toPath().getParent()
                    .resolve(baseName + "_chunks");

            Files.createDirectories(outputDir);

            Path outputPattern = outputDir.resolve(baseName + "_%03d.m4a");

            List<String> command = List.of(
                    ffmpegPath,               // usa PATH ou caminho absoluto
                    "-y",
                    "-i", inputFile.getAbsolutePath(),
                    "-f", "segment",
                    "-segment_time", String.valueOf(segmentSeconds),
                    "-c", "copy",
                    outputPattern.toAbsolutePath().toString()
            );

            log.info("Executando FFmpeg: {}", String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg retornou código: " + exitCode);
            }

            List<Path> chunks = new ArrayList<>();
            Files.list(outputDir)
                    .filter(p -> p.toString().endsWith(".m4a"))
                    .forEach(chunks::add);

            if (chunks.isEmpty()) {
                throw new RuntimeException("Nenhum chunk gerado");
            }

            log.info("Áudio dividido em {} partes", chunks.size());
            return chunks;

        } catch (Exception e) {
            log.error("Erro ao dividir áudio com FFmpeg", e);
            throw new RuntimeException("Falha ao dividir áudio", e);
        }
    }
}
