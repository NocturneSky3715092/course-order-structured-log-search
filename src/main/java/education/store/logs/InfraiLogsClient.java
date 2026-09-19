package education.store.logs;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InfraiLogsClient {
    private static final int MAX_ATTEMPTS = 4;
    private final AppConfig config;
    private final HttpClient http;

    public InfraiLogsClient(AppConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.timeout()).build());
    }

    InfraiLogsClient(AppConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public Map<String, Object> ingest(List<Map<String, Object>> entries, String idempotencyKey)
            throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entries", entries);
        body.put("idempotency_key", idempotencyKey);
        return call("POST", "/v1/logs/ingest", "", Json.stringify(body));
    }

    public Map<String, Object> search(String query, int limit) throws IOException, InterruptedException {
        String parameters = "q=" + encode(query)
                + "&service=" + encode(config.service())
                + "&environment=" + encode(config.environment())
                + "&limit=" + limit;
        return call("GET", "/v1/logs/search", parameters, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, String path, String query, String body)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String target = query.isEmpty() ? path : path + "?" + query;
            HttpRequest.Builder request = HttpRequest.newBuilder(config.baseUri().resolve(target))
                    .timeout(config.timeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
            else request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));

            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> envelope;
            try {
                envelope = Json.parseObject(response.body());
            } catch (IllegalArgumentException parseError) {
                throw new IOException("Infrai returned a non-JSON response with HTTP " + response.statusCode(), parseError);
            }
            if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                Thread.sleep(retryDelay(response, attempt).toMillis());
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Object rawError = envelope.get("error");
                Map<String, Object> error = rawError instanceof Map<?, ?> value
                        ? (Map<String, Object>) value : Map.of("message", "Request rejected");
                throw new InfraiException(response.statusCode(), error);
            }
            Object data = envelope.get("data");
            return data instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of("value", data);
        }
        throw new IOException("Retry attempts exhausted");
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        String header = response.headers().firstValue("Retry-After").orElse("");
        try { return Duration.ofSeconds(Math.max(1, Long.parseLong(header))); }
        catch (NumberFormatException ignored) { return Duration.ofMillis(250L * (1L << attempt)); }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static final class InfraiException extends IOException {
        private final int statusCode;
        private final Map<String, Object> error;

        InfraiException(int statusCode, Map<String, Object> error) {
            super(String.valueOf(error.getOrDefault("message", error)));
            this.statusCode = statusCode;
            this.error = Map.copyOf(error);
        }

        public int statusCode() { return statusCode; }
        public Map<String, Object> error() { return error; }
    }
}
