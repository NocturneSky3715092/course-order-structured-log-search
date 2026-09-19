package education.store.logs;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CourseOrderLogService {
    private final InfraiLogsClient logs;
    private final AppConfig config;

    public CourseOrderLogService(InfraiLogsClient logs, AppConfig config) {
        this.logs = logs;
        this.config = config;
    }

    public List<OrderEvent> planPaidOrder(PaidOrder order) {
        if (order.orderId().isBlank() || order.courseId().isBlank() || order.customerId().isBlank()) {
            throw new IllegalArgumentException("Order, course, and customer identifiers are required");
        }
        return List.of(
                new OrderEvent("checkout", "checkout.accepted", "Payment accepted for course order"),
                new OrderEvent("fulfillment", "fulfillment.queued", "Course enrollment queued"),
                new OrderEvent("receipt", "receipt.issued", "Receipt issued for paid order"),
                new OrderEvent("customer-update", "customer.order.updated", "Customer order marked fulfilled"));
    }

    public Map<String, Object> recordAndFind(PaidOrder order) throws IOException, InterruptedException {
        List<Map<String, Object>> entries = new ArrayList<>();
        Instant occurredAt = Instant.now();
        for (OrderEvent event : planPaidOrder(order)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", occurredAt.toString());
            entry.put("level", "info");
            entry.put("service", config.service());
            entry.put("environment", config.environment());
            entry.put("message", event.message());
            entry.put("event", event.name());
            entry.put("stage", event.stage());
            entry.put("order_id", order.orderId());
            entry.put("course_id", order.courseId());
            entry.put("customer_id", order.customerId());
            entries.add(entry);
        }

        // The order id makes a repeated job submission apply the same log batch once.
        logs.ingest(entries, "course-order-" + order.orderId());
        return logs.search("order_id:" + order.orderId(), 20);
    }

    public record PaidOrder(String orderId, String courseId, String customerId) {}
    public record OrderEvent(String stage, String name, String message) {}
}
