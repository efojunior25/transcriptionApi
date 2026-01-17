package com.xunim.transcriptionapi.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xunim.transcriptionapi.exception.WhisperApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhisperClient {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.whisper.url}")
    private String whisperUrl;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String transcribe(String filePath, String language) {

        String boundary = "----JavaBoundary" + System.currentTimeMillis();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(whisperUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(buildMultipartBody(filePath, language, boundary))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Whisper API retornou erro {}: {}", response.statusCode(), response.body());
                throw new WhisperApiException(
                        "Erro na API Whisper: " + response.body(),
                        response.statusCode()
                );
            }

            JsonNode json = MAPPER.readTree(response.body());
            return json.get("text").asText();

        } catch (IOException e) {
            log.error("Erro de I/O ao chamar Whisper API", e);
            throw new WhisperApiException("Erro ao processar arquivo de áudio", 500, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Requisição para Whisper API foi interrompida", e);
            throw new WhisperApiException("Processamento interrompido", 500, e);
        }
    }

    private HttpRequest.BodyPublisher buildMultipartBody(String filePath, String language, String boundary)
            throws IOException {

        List<byte[]> parts = new ArrayList<>();
        String separator = "--" + boundary + "\r\n";

        // model
        parts.add(separator.getBytes());
        parts.add("Content-Disposition: form-data; name=\"model\"\r\n\r\n".getBytes());
        parts.add("whisper-1\r\n".getBytes());

        // language (opcional)
        if (language != null && !language.trim().isEmpty()) {
            parts.add(separator.getBytes());
            parts.add("Content-Disposition: form-data; name=\"language\"\r\n\r\n".getBytes());
            parts.add((language + "\r\n").getBytes());
        }

        // file
        parts.add(separator.getBytes());
        String filename = Path.of(filePath).getFileName().toString();
        parts.add(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes());
        parts.add("Content-Type: audio/mpeg\r\n\r\n".getBytes());
        parts.add(Files.readAllBytes(Path.of(filePath)));
        parts.add("\r\n".getBytes());

        // end
        parts.add(("--" + boundary + "--").getBytes());

        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }
}