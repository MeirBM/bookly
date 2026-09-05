package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * Turn-2 criterion 2.28, server side: the API answers a cross-origin preflight from the dashboard's
 * configured origin and refuses one from any other.
 *
 * <p>This exists because a browser found what 113 HTTP tests could not. Every other integration
 * test here speaks HTTP directly, where a preflight does not exist — so an API that was entirely
 * correct on every request a test made was unreachable from the browser it was built for. The
 * browser test in {@code dashboard.spec.ts} remains the one that proves the dashboard works; this
 * one catches a regression without needing a browser to be running.
 *
 * <p>The allowed origins are set here rather than left to the default, so the test states its own
 * premise instead of depending on what {@code application.yml} happens to say.
 */
@TestPropertySource(properties =
        "bookly.cors.allowed-origins=http://localhost:3000,https://dashboard.bookly.example")
class CorsContractIT extends ApiIntegrationTest {

    private static final String ALLOWED = "http://localhost:3000";
    private static final String ALSO_ALLOWED = "https://dashboard.bookly.example";
    private static final String STRANGER = "https://evil.example";

    private ResponseEntity<String> preflight(String origin, String method, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ORIGIN, origin);
        headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
        headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type");
        return rest.exchange(path, HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    }

    /**
     * The preflight must be answered <em>before</em> authorization. A browser sends no credentials
     * on it, by construction, so a filter chain that requires them refuses every call the dashboard
     * will ever make — and refuses it in a way the page can only report as a generic failure.
     */
    @Test
    @DisplayName("2.28 a preflight from an allowed origin is answered without credentials")
    void preflightFromTheDashboardOriginIsAllowed() {
        for (String origin : List.of(ALLOWED, ALSO_ALLOWED)) {
            ResponseEntity<String> response =
                    preflight(origin, "GET", "/api/businesses/" + java.util.UUID.randomUUID());

            assertThat(response.getStatusCode().value())
                    .as("preflight from %s carries no Authorization header, so requiring one here "
                            + "makes the API unreachable from that origin", origin)
                    .isLessThan(400);
            assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin"))
                    .as("the response must name the origin it allows")
                    .isEqualTo(origin);
            assertThat(String.valueOf(response.getHeaders().getFirst("Access-Control-Allow-Methods")))
                    .as("the method the browser asked about must be allowed")
                    .contains("GET");
            assertThat(String.valueOf(response.getHeaders().getFirst("Access-Control-Allow-Headers"))
                            .toLowerCase(java.util.Locale.ROOT))
                    .as("the dashboard sends its token in an Authorization header, so the preflight "
                            + "must allow that header or every authenticated call fails")
                    .contains("authorization");
        }
    }

    /** 2.28, the other direction — an origin nobody configured gets nothing. */
    @Test
    @DisplayName("2.28 a preflight from an unconfigured origin is refused")
    void preflightFromAnotherOriginIsRefused() {
        ResponseEntity<String> response = preflight(STRANGER, "GET", "/api/businesses");

        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin"))
                .as("an origin that is not configured must not be told it is allowed; a wildcard "
                        + "here would let any page on the internet read a signed-in user's data")
                .isNull();
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("the preflight from %s must not succeed", STRANGER)
                .isFalse();
    }

    /**
     * The preflight is only half of it: the browser also checks the real response. An API that
     * allows the preflight and then omits the header on the response itself fails just as
     * completely, and fails in a way that only shows up on the second request.
     */
    @Test
    @DisplayName("2.28 the actual response also carries the allow-origin header")
    void actualResponseCarriesTheAllowOriginHeader() {
        Account owner = newAccount("cors");
        String businessId = newBusiness(owner, "CORS Salon", "Asia/Jerusalem").path("id").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ORIGIN, ALLOWED);
        headers.setBearerAuth(owner.accessToken());
        ResponseEntity<String> response = rest.exchange(
                "/api/businesses/" + businessId, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).as("the request itself succeeds").isEqualTo(200);
        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin"))
                .as("without this header on the real response the browser discards a body the "
                        + "server went to the trouble of producing")
                .isEqualTo(ALLOWED);
        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Credentials"))
                .as("credentials travel in an Authorization header, not a cookie, so nothing here "
                        + "should be asking the browser to attach ambient credentials")
                .isNotEqualTo("true");
    }
}
