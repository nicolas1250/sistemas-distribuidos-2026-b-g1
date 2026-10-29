import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Story: "[security] csp-ticketing-api accepts only tokens signed by csp-auth-api" (threat S).
 * Run: java TicketingAuthzNegativeTest.java   (BASE_URL defaults to http://localhost:8084/api/v1/ticketing)
 */
public class TicketingAuthzNegativeTest {

    private static final String BASE = System.getenv().getOrDefault("BASE_URL", "http://localhost:8084/api/v1/ticketing");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        expect401("GET /me/tickets without Authorization", null);
        expect401("GET /me/tickets with an alg=none token", "Bearer " + unsignedToken());
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL (" + failures + " failed)");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void expect401(String name, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE + "/me/tickets")).GET();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        int status = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
        boolean ok = status == 401;
        System.out.println((ok ? "PASS " : "FAIL ") + name + " -> HTTP " + status + " (expected 401)");
        if (!ok) {
            failures++;
        }
    }

    private static String unsignedToken() {
        long exp = System.currentTimeMillis() / 1000 + 900;
        String header = "{\"alg\":\"none\",\"typ\":\"JWT\"}";
        String payload = "{\"sub\":\"550e8400-e29b-41d4-a716-446655440000\",\"exp\":" + exp
                + ",\"permissions\":[\"ticket:read:self\"]}";
        return base64Url(header) + "." + base64Url(payload) + ".";
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
