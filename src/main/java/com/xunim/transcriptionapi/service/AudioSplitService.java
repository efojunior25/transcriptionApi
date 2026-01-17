package com.xunim.transcriptionapi.service;

import com.xunim.transcriptionapi.exception.AudioProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AudioSplitService {

    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    public List<Path> splitAudio(String inputFilePath, int segmentSeconds) {

        try {
            File inputFile = new File(inputFilePath);

            if (!inputFile.exists()) {
                throw new AudioProcessingException("Arquivo de áudio não encontrado: " + inputFilePath);
            }

            String baseName = inputFile.getName().replaceFirst("\\.[^.]+$", "");
            Path outputDir = inputFile.toPath().getParent()
                    .resolve(baseName + "_chunks");

            Files.createDirectories(outputDir);

            Path outputPattern = outputDir.resolve(baseName + "_%03d.m4a");

            List<String> command = List.of(
                    ffmpegPath,
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

            // Captura saída do FFmpeg para debugging
            String output = captureProcessOutput(process);

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("FFmpeg falhou com código {}: {}", exitCode, output);
                throw new AudioProcessingException(
                        "Falha ao processar áudio. Verifique se o arquivo está corrompido."
                );
            }

            List<Path> chunks = new ArrayList<>();
            Files.list(outputDir)
                    .filter(p -> p.toString().endsWith(".m4a"))
                    .sorted()
                    .forEach(chunks::add);

            if (chunks.isEmpty()) {
                throw new AudioProcessingException(
                        "Nenhum chunk gerado. O arquivo pode estar corrompido ou muito curto."
                );
            }

            log.info("Áudio dividido em {} partes", chunks.size());
            return chunks;

        } catch (AudioProcessingException e) {
            throw e; // Re-lança exceções já tratadas
        } catch (Exception e) {
            log.error("Erro inesperado ao dividir áudio", e);
            throw new AudioProcessingException("Erro ao processar arquivo de áudio", e);
        }
    }

    private String captureProcessOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "";
        }
    }
}