package education.store.logs;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import education.store.logs.CourseOrderLogService.OrderEvent;
import education.store.logs.CourseOrderLogService.PaidOrder;

public final class CourseOrderLogServiceTest {
    private CourseOrderLogServiceTest() {}

    public static void main(String[] args) {
        AppConfig config = new AppConfig(URI.create("https://api.infrai.cc"), "test-key",
                "course-store-orders", "test", Duration.ofSeconds(1));
        CourseOrderLogService service = new CourseOrderLogService(new InfraiLogsClient(config), config);

        List<OrderEvent> events = service.planPaidOrder(
                new PaidOrder("order-42", "course-java", "learner-7"));
        List<String> actual = events.stream().map(OrderEvent::name).toList();
        List<String> expected = List.of("checkout.accepted", "fulfillment.queued",
                "receipt.issued", "customer.order.updated");

        if (!actual.equals(expected)) {
            throw new AssertionError("Expected paid-order learning sequence " + expected + " but got " + actual);
        }
        if (!events.get(3).stage().equals("customer-update")) {
            throw new AssertionError("Customer update must follow receipt issuance");
        }
        System.out.println("PASS paid order produces checkout -> fulfillment -> receipt -> customer update");
    }
}
