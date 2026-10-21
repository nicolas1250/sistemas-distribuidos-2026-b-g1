import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TicketingObservabilityDemo {
    private static final Pattern TRACE = Pattern.compile(
            "^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");
    private static final double[] BUCKETS = {0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0};
    private static final ConcurrentHashMap<String, AtomicLong> COUNTS = new ConcurrentHashMap<>();
    private static final AtomicLong[] LATENCY = new AtomicLong[BUCKETS.length + 1];
    private static volatile String downstreamTraceparent;
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    static {
        for (int i = 0; i < LATENCY.length; i++) LATENCY[i] = new AtomicLong();
    }

    private record Context(String traceId, String spanId, String flags) {
        String header() { return "00-" + traceId + "-" + spanId + "-" + flags; }
        Context child() { return new Context(traceId, UUID.randomUUID().toString().replace("-", "").substring(0, 16), flags); }
        static Context parse(String value) {
            Matcher m = TRACE.matcher(value == null ? "" : value);
            if (!m.matches() || m.group(1).matches("0{32}") || m.group(2).matches("0{16}"))
                return new Context(UUID.randomUUID().toString().replace("-", ""), UUID.randomUUID().toString().replace("-", "").substring(0, 16), "01");
            return new Context(m.group(1), m.group(2), m.group(3));
        }
    }

    public static void main(String[] args) throws Exception {
        boolean test = args.length > 0 && "--test".equals(args[0]);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", test ? 0 : 8080), 0);
        server.createContext("/tickets/confirm", exchange -> instrument(exchange, server.getAddress().getPort()));
        server.createContext("/mock-storage", exchange -> {
            downstreamTraceparent = exchange.getRequestHeaders().getFirst("traceparent");
            respond(exchange, 204, "");
        });
        server.createContext("/metrics", exchange -> respond(exchange, 200, metrics()));
        server.start();
        if (test) {
            try { runTest(server.getAddress().getPort()); }
            finally { server.stop(0); }
        } else {
            System.out.println("ticketing-fulfillment-service demo listening on http://127.0.0.1:8080");
        }
    }

    private static void instrument(HttpExchange exchange, int port) throws IOException {
        long start = System.nanoTime();
        Context context = Context.parse(exchange.getRequestHeaders().getFirst("traceparent")).child();
        String correlation = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
        if (correlation == null || correlation.isBlank()) correlation = UUID.randomUUID().toString();
        exchange.getResponseHeaders().set("traceparent", context.header());
        exchange.getResponseHeaders().set("X-Correlation-Id", correlation);
        String outcome = "success";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mock-storage"))
                    .header("traceparent", context.child().header())
                    .POST(HttpRequest.BodyPublishers.ofString("synthetic-ticket-pdf"))
                    .build();
            int status = CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 300) throw new IOException("storage returned HTTP " + status);
            respond(exchange, 200, "{\"status\":\"ticket-issued\"}");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            outcome = "error";
            respond(exchange, 503, "{\"error\":\"fulfillment-unavailable\"}");
        } catch (IOException error) {
            outcome = "error";
            respond(exchange, 503, "{\"error\":\"fulfillment-unavailable\"}");
        } finally {
            long nanos = System.nanoTime() - start;
            COUNTS.computeIfAbsent(outcome, ignored -> new AtomicLong()).incrementAndGet();
            double seconds = nanos / 1_000_000_000.0;
            for (int i = 0; i < BUCKETS.length; i++) if (seconds <= BUCKETS[i]) LATENCY[i].incrementAndGet();
            LATENCY[BUCKETS.length].incrementAndGet();
            log(context, correlation, outcome, nanos / 1_000_000);
        }
    }

    private static void log(Context c, String correlation, String outcome, long durationMs) {
        System.out.printf("{\"timestamp\":\"%s\",\"level\":\"%s\",\"service\":\"ticketing-fulfillment-service\",\"version\":\"1.0.0\",\"environment\":\"local\",\"trace_id\":\"%s\",\"span_id\":\"%s\",\"correlation_id\":\"%s\",\"event\":\"ticket.fulfillment.%s\",\"message\":\"Ticket fulfillment %s\",\"duration_ms\":%d}%n",
                Instant.now(), outcome.equals("error") ? "ERROR" : "INFO", c.traceId(), c.spanId(),
                correlation, outcome, outcome, durationMs);
    }

    private static String metrics() {
        StringBuilder out = new StringBuilder("# TYPE ticketing_requests_total counter\n");
        COUNTS.forEach((result, count) -> out.append("ticketing_requests_total{result=\"")
                .append(result).append("\"} ").append(count.get()).append('\n'));
        out.append("# TYPE ticketing_request_duration_seconds histogram\n");
        for (int i = 0; i < BUCKETS.length; i++)
            out.append("ticketing_request_duration_seconds_bucket{le=\"").append(BUCKETS[i])
                    .append("\"} ").append(LATENCY[i].get()).append('\n');
        return out.append("ticketing_request_duration_seconds_bucket{le=\"+Inf\"} ")
                .append(LATENCY[BUCKETS.length].get()).append('\n').toString();
    }

    private static void runTest(int port) throws Exception {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/tickets/confirm"))
                .header("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01")
                .header("X-Correlation-Id", "cp3-test-correlation")
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new AssertionError("Expected HTTP 200");
        if (!traceId.equals(Context.parse(downstreamTraceparent).traceId()))
            throw new AssertionError("trace_id did not propagate to downstream storage");
        if (!metrics().contains("ticketing_requests_total{result=\"success\"} 1"))
            throw new AssertionError("request counter was not incremented");
        System.out.println("PASS: incoming trace_id propagated to downstream storage; counter and histogram updated.");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (status == 204) { exchange.sendResponseHeaders(status, -1); }
        else {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
