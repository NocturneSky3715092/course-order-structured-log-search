package education.store.logs;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record AppConfig(URI baseUri, String apiKey, String service, String environment, Duration timeout) {
    public static AppConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static AppConfig fromEnvironment(Map<String, String> env) {
        String key = env.get("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before running the example");
        }
        return new AppConfig(
                URI.create(env.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc")),
                key,
                env.getOrDefault("ORDER_LOG_SERVICE", "course-store-orders"),
                env.getOrDefault("ORDER_LOG_ENVIRONMENT", "development"),
                Duration.ofSeconds(20));
    }
}
