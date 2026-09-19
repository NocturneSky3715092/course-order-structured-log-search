package education.store.logs;

import java.util.Map;

import education.store.logs.CourseOrderLogService.PaidOrder;

public final class CourseOrderJob {
    private CourseOrderJob() {}

    public static void main(String[] args) throws Exception {
        String orderId = args.length > 0 ? args[0] : "order-course-1042";
        AppConfig config = AppConfig.fromEnvironment();
        CourseOrderLogService service = new CourseOrderLogService(new InfraiLogsClient(config), config);

        Map<String, Object> searchResult = service.recordAndFind(
                new PaidOrder(orderId, "java-observability-101", "learner-27"));

        System.out.println("Recorded checkout through customer update for " + orderId);
        System.out.println("Search result: " + Json.stringify(searchResult));
    }
}
