package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.bookly.support.ApiIntegrationTest;
import com.bookly.support.LogCapture;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.bookly.support.Routes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.assertj.core.api.SoftAssertions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.test.context.TestPropertySource;

/**
 * Turn-1 criterion 1.22: the unauthenticated {@code /api/auth/*} endpoints are rate limited per
 * caller, and a failed login is recorded in the log without naming the account.
 *
 * <p>The limit matters beyond nuisance traffic: the accepted risk recorded in the spec is that
 * {@code POST /api/auth/register} answers 409 for an address that already has an account, which
 * makes it an account-enumeration oracle. Rate limiting is what bounds how fast that list can be
 * walked, so this suite is the evidence behind that acceptance.
 *
 * <p>The shared profile sets the limit effectively off, because a limit tripping mid-suite would
 * look like a defect in the code under test. This class sets its own low limit, and clears the
 * counters before each test: every suite in this JVM calls {@code /api/auth/*} from the same
 * address, so without that the outcome here would be decided by how many requests the classes that
 * happened to run first had made. Spec part 3 says Redis holds the rate-limit counters and nothing
 * else, so flushing it isolates this suite without discarding anything another suite needs.
 *
 * <p>The window is a whole minute so that no boundary can fall inside a burst; the counters start
 * empty, so the burst below meets the limit and not the clock.
 */
@TestPropertySource(properties = {
    "bookly.security.rate-limit.max-requests=" + AuthRateLimitIT.MAX_REQUESTS,
    "bookly.security.rate-limit.api-max-requests=" + AuthRateLimitIT.MAX_REQUESTS,
    "bookly.security.rate-limit.window=PT1M"
})
class AuthRateLimitIT extends ApiIntegrationTest {

    static final int MAX_REQUESTS = 5;

    @Autowired
    private RedisConnectionFactory redis;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @BeforeEach
    void startWithEmptyCounters() {
        try (RedisConnection connection = redis.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    /** 1.22 — past the limit the caller is refused with 429, a Retry-After and the standard body. */
    @Test
    @DisplayName("1.22 requests past the limit are refused with 429 and a Retry-After")
    void requestsPastTheLimitAreRefusedWithTooManyRequests() {
        List<ResponseEntity<String>> responses = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            responses.add(login("nobody-" + i + "@example.test", "not-the-password-here"));
        }

        List<ResponseEntity<String>> refused =
                responses.stream().filter(r -> r.getStatusCode().value() == 429).toList();
        long admitted = responses.stream().filter(r -> r.getStatusCode().value() != 429).count();

        assertThat(refused)
                .as("30 unauthenticated requests from one address against a limit of %d per minute "
                        + "must be refused at some point", MAX_REQUESTS)
                .isNotEmpty();
        // Whether the limit admits exactly max-requests or one fewer is not something the spec
        // fixes, so the assertion is that the configured number is the ceiling and that the
        // limiter does not simply refuse everything.
        assertThat(admitted)
                .as("requests admitted from an empty counter against a limit of %d", MAX_REQUESTS)
                .isBetween(1L, (long) MAX_REQUESTS);

        ResponseEntity<String> first = refused.get(0);
        assertThat(first.getHeaders().getFirst("Retry-After"))
                .as("a 429 must tell the caller when to come back")
                .isNotBlank();

        JsonNode error = json(first);
        assertThat(error.path("code").asText())
                .as("the standard error shape from CLAUDE.md: code, message, fieldErrors")
                .isEqualTo("RATE_LIMITED");
        assertThat(error.path("message").asText())
                .as("the error message")
                .isNotBlank();
        assertThat(String.valueOf(first.getBody()))
                .as("an error body never carries a stack trace")
                .doesNotContain("\tat ", ".java:", "Exception");
    }

    /**
     * 1.22 — a failed login is recorded, and the record does not name the account. Logging the
     * attempted address would write an attacker's guess list into the log, where it becomes a
     * ready-made list of addresses worth attacking and outlives the attack itself.
     */
    @Test
    @DisplayName("1.22 a failed login is logged without the attempted email address")
    void failedLoginIsLoggedWithoutTheEmailAddress() throws Exception {
        String email = uniqueEmail("watched");
        assertThat(register(email, FIXTURE_PASSWORD, "Watched User").getStatusCode().value())
                .as("registration")
                .isEqualTo(201);
        String localPart = email.substring(0, email.indexOf('@'));

        List<ILoggingEvent> events = LogCapture.around(() -> assertThat(
                        login(email, "definitely-not-the-password").getStatusCode().value())
                .as("a failed login, which must not be refused as rate limited in this window")
                .isEqualTo(401));

        assertThat(events)
                .as("the capture must have been listening")
                .anyMatch(e -> LogCapture.PROBE.equals(e.getFormattedMessage()));
        assertThat(events)
                .as("a failed login must leave a trace: an authentication failure that is logged "
                        + "nowhere at any level cannot be noticed, alerted on, or investigated")
                .anyMatch(e -> e.getLoggerName().startsWith("com.bookly")
                        && !LogCapture.PROBE.equals(e.getFormattedMessage()));

        for (ILoggingEvent event : events) {
            String rendered = LogCapture.render(event).toLowerCase(Locale.ROOT);
            assertThat(rendered)
                    .as("log line from %s must not name the account that was attempted",
                            event.getLoggerName())
                    .doesNotContain(email.toLowerCase(Locale.ROOT))
                    .doesNotContain(localPart.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * 2.30 — every {@code /api} route is rate limited, not only {@code /api/auth}.
     *
     * <p>Limiting the login endpoints alone leaves everything this turn added unbounded: an
     * authenticated caller could drive the availability engine as fast as the network allows, and
     * that is the expensive route, not login. The set is read from the route table rather than
     * listed here, so a route added later is covered without anyone remembering to add it.
     */
    @Test
    @DisplayName("2.30 every /api route is rate limited, not only /api/auth")
    void everyApiRouteIsRateLimited() {
        Account owner = newAccount("api-limit");
        String businessId = newBusiness(owner, "Limited Salon", "Asia/Jerusalem").path("id").asText();

        // Spend the budget on one route; the limiter counts the caller, not the endpoint.
        int attempts = 0;
        while (attempts < 50
                && get("/api/businesses", owner.accessToken()).getStatusCode().value() != 429) {
            attempts++;
        }
        assertThat(attempts)
                .as("GET /api/businesses must become rate limited; after %d requests it had not, "
                        + "so the limiter does not reach the routes this turn added", attempts)
                .isLessThan(50);

        List<Routes.Route> routes = Routes.requiringIsolationCoverage(handlerMapping);
        assertThat(routes).as("routes under /api that are not the public auth endpoints").isNotEmpty();

        SoftAssertions soft = new SoftAssertions();
        for (Routes.Route route : routes) {
            String path = Routes.fill(route.pattern(), businessId);
            Object requestBody = route.method() == HttpMethod.GET ? null : body();
            ResponseEntity<String> response =
                    send(route.method(), path, requestBody, owner.accessToken());

            soft.assertThat(response.getStatusCode().value())
                    .as("%s once the caller is over the limit; an unlimited route is the one an "
                            + "attacker will find", route)
                    .isEqualTo(429);
        }
        soft.assertAll();
    }
}
