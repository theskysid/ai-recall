package com.theskysid.echobackend.call.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class DeepgramService {

    private static final String DEEPGRAM_ENDPOINT =
            "https://api.deepgram.com/v1/listen?model=nova-2&smart_format=true";

    @Value("${deepgram.api-key:}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Send a hosted audio URL to Deepgram's batch transcription endpoint and
     * return the resulting transcript text.
     */
    public String transcribe(String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            throw new RuntimeException("Audio URL is required");
        }

        String requestBody = "{\"url\":\"" + audioUrl.trim().replace("\"", "\\\"") + "\"}";
        return send(HttpRequest.BodyPublishers.ofString(requestBody), "application/json");
    }

    /**
     * Send raw audio bytes (e.g. a browser MediaRecorder capture) to the same
     * batch endpoint. Deepgram sniffs the container, so contentType only needs
     * to be roughly right.
     */
    public String transcribe(byte[] audio, String contentType) {
        if (audio == null || audio.length == 0) {
            throw new RuntimeException("Audio file is empty");
        }

        String type = (contentType == null || contentType.isBlank()) ? "audio/webm" : contentType;
        return send(HttpRequest.BodyPublishers.ofByteArray(audio), type);
    }

    /**
     * POST a body to Deepgram and return the transcript from the response.
     */
    private String send(HttpRequest.BodyPublisher body, String contentType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("Deepgram is not configured");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DEEPGRAM_ENDPOINT))
                .header("Authorization", "Token " + apiKey)
                .header("Content-Type", contentType)
                .timeout(Duration.ofMinutes(5))
                .POST(body)
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Deepgram request failed with status " + response.statusCode());
            }
            return extractTranscript(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to reach Deepgram: " + e.getMessage());
        }
    }

    /**
     * Pull the transcript string out of Deepgram's JSON response:
     * results.channels[0].alternatives[0].transcript
     */
    private String extractTranscript(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode transcript = root
                    .path("results")
                    .path("channels").path(0)
                    .path("alternatives").path(0)
                    .path("transcript");
            return transcript.isMissingNode() ? "" : transcript.asText("");
        } catch (Exception e) {
            throw new RuntimeException("Could not parse Deepgram response");
        }
    }
}
