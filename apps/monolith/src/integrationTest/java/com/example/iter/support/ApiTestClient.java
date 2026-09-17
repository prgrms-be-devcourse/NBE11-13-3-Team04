package com.example.iter.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class ApiTestClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final JsonMapper jsonMapper;
    private final String baseUrl;

    public ApiTestClient(int port, JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.baseUrl = "http://127.0.0.1:" + port;
    }

    public ApiResponse post(String path, Map<String, ?> body, String accessToken) {
        return exchange("POST", path, body, Map.of("Authorization", "Bearer " + accessToken));
    }

    public ApiResponse post(String path, Map<String, ?> body, Map<String, String> headers) {
        return exchange("POST", path, body, headers);
    }

    public ApiResponse get(String path, Map<String, String> headers) {
        return exchange("GET", path, null, headers);
    }

    public ApiResponse patch(String path, String accessToken) {
        return exchange("PATCH", path, null, Map.of("Authorization", "Bearer " + accessToken));
    }

    public ApiResponse patch(String path, Map<String, ?> body, String accessToken) {
        return exchange("PATCH", path, body, Map.of("Authorization", "Bearer " + accessToken));
    }

    public ApiResponse delete(String path, String accessToken) {
        return exchange("DELETE", path, null, Map.of("Authorization", "Bearer " + accessToken));
    }

    private ApiResponse exchange(
            String method,
            String path,
            Map<String, ?> body,
            Map<String, String> headers
    ) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10));
            headers.forEach(request::header);

            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)));
            }

            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = response.body() == null || response.body().isBlank()
                    ? null
                    : jsonMapper.readTree(response.body());
            return new ApiResponse(response.statusCode(), response.body(), json, response.headers());
        } catch (IOException e) {
            throw new IllegalStateException("통합 테스트 HTTP 요청에 실패했습니다.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("통합 테스트 HTTP 요청이 중단되었습니다.", e);
        }
    }

    public record ApiResponse(int statusCode, String body, JsonNode json, HttpHeaders headers) {
    }
}
