package iter.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public final class TossStub {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";

    private final HttpServer server;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile boolean failConfirm;
    private volatile boolean failCancel;
    private volatile String lookupOrderId = "stub-order";
    private volatile String lookupStatus = "DONE";

    private TossStub(HttpServer server) {
        this.server = server;
    }

    public static TossStub start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            TossStub stub = new TossStub(server);
            server.createContext("/", stub::handle);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "toss-integration-stub");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return stub;
        } catch (IOException e) {
            throw new IllegalStateException("Toss 통합 테스트 스텁을 시작할 수 없습니다.", e);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reset() {
        failConfirm = false;
        failCancel = false;
        lookupOrderId = "stub-order";
        lookupStatus = "DONE";
        requests.clear();
    }

    public void failConfirm() {
        failConfirm = true;
    }

    public void succeedConfirm() {
        failConfirm = false;
    }

    public void failCancel() {
        failCancel = true;
    }

    public void succeedCancel() {
        failCancel = false;
    }

    public void lookupPayment(String orderId, String status) {
        lookupOrderId = orderId;
        lookupStatus = status;
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                body));

        String path = exchange.getRequestURI().getPath();
        if (CONFIRM_PATH.equals(path) && "POST".equals(exchange.getRequestMethod())) {
            handleConfirm(exchange);
            return;
        }
        if (path.matches("/v1/payments/[^/]+/cancel") && "POST".equals(exchange.getRequestMethod())) {
            handleCancel(exchange);
            return;
        }
        if (path.matches("/v1/payments/[^/]+") && "GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 200, lookupResponse());
            return;
        }
        respond(exchange, 404, "{\"code\":\"NOT_FOUND\"}");
    }

    private void handleConfirm(HttpExchange exchange) throws IOException {
        if (failConfirm) {
            respond(exchange, 500, "{\"code\":\"STUB_FAILURE\",\"message\":\"forced failure\"}");
            return;
        }
        respond(exchange, 200, successResponse());
    }

    private void handleCancel(HttpExchange exchange) throws IOException {
        if (failCancel) {
            respond(exchange, 500, "{\"code\":\"STUB_CANCEL_FAILURE\",\"message\":\"forced failure\"}");
            return;
        }
        respond(exchange, 200, successResponse());
    }

    private String successResponse() {
        return """
                {"paymentKey":"toss-payment-key","orderId":"stub-order","status":"DONE",
                 "approvedAt":"2026-09-17T10:00:00+09:00","totalAmount":100000}
                """;
    }

    private String lookupResponse() {
        return """
                {"paymentKey":"toss-payment-key","orderId":"%s","status":"%s",
                 "approvedAt":"2026-09-17T10:00:00+09:00","totalAmount":100000}
                """.formatted(lookupOrderId, lookupStatus);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }

    public record RecordedRequest(
            String method,
            String path,
            String authorization,
            String idempotencyKey,
            String body
    ) {
    }
}
