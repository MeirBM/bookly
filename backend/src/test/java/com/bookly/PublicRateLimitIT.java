package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import com.bookly.support.Routes;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Turn-3 criterion 3.15: every public route is rate limited by address, more strictly than the
 * authenticated API.
 *
 * <p>The public surface is the only part of the system a stranger can reach, and the only part
 * where the caller has no account to suspend. It is also the expensive part — availability is
 * computed per request. So the budget here is smaller than the one an authenticated caller gets,
 * and this suite states both halves: that the public limit bites, and that it bites sooner.
 *
 * <p>The two limits are set here rather than left to the profile, so the test states its premise
 * instead of depending on what {@code application.yml} happens to say, and the counters are cleared
 * before each test because every suite in this JVM calls from the same address.
 */
@TestPropertySource(properties = {
    "bookly.security.rate-limit.public-max-requests=" + PublicRateLimitIT.PUBLIC_LIMIT,
    "bookly.security.rate-limit.api-max-requests=" + PublicRateLimitIT.API_LIMIT,
    "bookly.security.rate-limit.max-requests=" + PublicRateLimitIT.API_LIMIT,
    "bookly.security.rate-limit.window=PT1M"
})
class PublicRateLimitIT extends ApiIntegrationTest {

    static final int PUBLIC_LIMIT = 5;
    static final int API_LIMIT = 60;

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

    /** 3.15 — the public surface is refused past its limit, with the standard body. */
    @Test
    @DisplayName("3.15 the public surface is rate limited by address")
    void publicSurfaceIsRateLimited() {
        Bookable bookable = newBookableBusiness("public-limit", 60);

        List<ResponseEntity<String>> responses = new ArrayList<>();
        for (int i = 0; i < PUBLIC_LIMIT * 4; i++) {
            responses.add(publicBusiness(bookable.slug()));
        }

        List<ResponseEntity<String>> refused =
                responses.stream().filter(r -> r.getStatusCode().value() == 429).toList();
        long admitted = responses.stream().filter(r -> r.getStatusCode().value() != 429).count();

        assertThat(refused)
                .as("an anonymous caller has no account to suspend, so the address is the only "
                        + "handle there is; without a limit the public page is a free amplifier")
                .isNotEmpty();
        assertThat(admitted)
                .as("requests admitted from an empty counter against a public limit of %d", PUBLIC_LIMIT)
                .isBetween(1L, (long) PUBLIC_LIMIT);

        ResponseEntity<String> first = refused.get(0);
        assertThat(first.getHeaders().getFirst("Retry-After"))
                .as("a 429 must tell the caller when to come back")
                .isNotBlank();
        JsonNode error = json(first);
        assertThat(error.path("code").asText())
                .as("the standard error shape, with a code a client can branch on")
                .isEqualTo("RATE_LIMITED");
        assertThat(String.valueOf(first.getBody()))
                .as("and no stack trace, as everywhere else")
                .doesNotContain("\tat ", ".java:", "Exception");
    }

    /**
     * 3.15 — <em>every</em> public route, generated from the route table so one added later is
     * covered without anyone remembering to add it here.
     */
    @Test
    @DisplayName("3.15 every public route is limited, not only the one that is easy to test")
    void everyPublicRouteIsRateLimited() {
        Bookable bookable = newBookableBusiness("public-limit-all", 60);
        Instant slot = firstAvailableStart(bookable);

        List<Routes.Route> routes = Routes.publicSurface(handlerMapping);
        assertThat(routes).as("routes under %s", Routes.PUBLIC_PREFIX).isNotEmpty();

        // Spend the public budget on one route; the limiter counts the caller and the surface.
        int attempts = 0;
        while (attempts < 50 && publicBusiness(bookable.slug()).getStatusCode().value() != 429) {
            attempts++;
        }
        assertThat(attempts).as("the public surface must become limited").isLessThan(50);

        SoftAssertions soft = new SoftAssertions();
        for (Routes.Route route : routes) {
            String path = Routes.fill(route.pattern(), Map.of("slug", bookable.slug()));
            String query = route.pattern().endsWith("/availability")
                    ? "?serviceId=" + bookable.serviceId() + "&date=" + BOOKING_DATE
                    : "";
            Object requestBody = route.method() == HttpMethod.GET
                    ? null
                    : bookingBody(bookable.serviceId(), bookable.employeeId(), slot,
                            UUID.randomUUID() + "@example.test");

            ResponseEntity<String> response = send(route.method(), path + query, requestBody, null);

            soft.assertThat(response.getStatusCode().value())
                    .as("%s once the caller has spent the public budget; a public route with its "
                            + "own untouched allowance is the one an attacker will find", route)
                    .isEqualTo(429);
        }
        soft.assertAll();
    }

    /** 3.15 — "more strictly than the authenticated API" is the half that is easy to leave untested. */
    @Test
    @DisplayName("3.15 the public limit is stricter than the authenticated one")
    void thePublicLimitIsStricterThanTheAuthenticatedApi() {
        Bookable bookable = newBookableBusiness("public-vs-api", 60);

        int publicAdmitted = 0;
        while (publicAdmitted < API_LIMIT && publicBusiness(bookable.slug()).getStatusCode().value() != 429) {
            publicAdmitted++;
        }
        assertThat(publicAdmitted)
                .as("the public surface must run out before the authenticated budget of %d could", API_LIMIT)
                .isLessThan(API_LIMIT);

        // The authenticated surface still answers: the budgets are separate, and the public one is
        // the smaller. If a single shared counter governed both, this would already be 429.
        ResponseEntity<String> authenticated =
                get(businessPath(bookable.businessId(), "/services"), bookable.owner().accessToken());
        assertThat(authenticated.getStatusCode().value())
                .as("an owner working in their dashboard must not be locked out because a stranger "
                        + "hammered the public page from the same address")
                .isEqualTo(200);
    }
}
