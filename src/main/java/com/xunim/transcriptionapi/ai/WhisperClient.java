package com.xunim.transcriptionapi.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
public class WhisperClient {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.whisper.url}")
    private String whisperUrl;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String transcribe(String filePath) throws Exception {

        String boundary = "----JavaBoundary" + System.currentTimeMillis();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(whisperUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(buildMultipartBody(filePath, boundary))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Erro Whisper (" + response.statusCode() + "): " + response.body()
            );
        }

        JsonNode json = MAPPER.readTree(response.body());
        return json.get("text").asText();
    }

    private HttpRequest.BodyPublisher buildMultipartBody(String filePath, String boundary)
            throws IOException {

        List<byte[]> parts = new ArrayList<>();
        String separator = "--" + boundary + "\r\n";

        // model
        parts.add(separator.getBytes());
        parts.add(
                "Content-Disposition: form-data; name=\"model\"\r\n\r\n"
                        .getBytes()
        );
        parts.add("whisper-1\r\n".getBytes());

        // file
        parts.add(separator.getBytes());
        parts.add(
                ("Content-Disposition: form-data; name=\"file\"; filename=\"audio.mp3\"\r\n")
                        .getBytes()
        );
        parts.add("Content-Type: audio/mpeg\r\n\r\n".getBytes());
        parts.add(Files.readAllBytes(Path.of(filePath)));
        parts.add("\r\n".getBytes());

        // end
        parts.add(("--" + boundary + "--").getBytes());

        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }
}
