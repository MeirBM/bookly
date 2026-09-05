package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import com.bookly.support.Routes;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Turn-1 criteria 1.2, 1.3, 1.4, 1.5 and 1.8.
 *
 * <p>The access-token lifetime is cut to two seconds for this class so that criterion 1.8 can
 * observe a genuinely expired token issued by the application itself, rather than one this test
 * minted from assumptions about the claim structure.
 */
@TestPropertySource(properties = "bookly.jwt.access-token-ttl=PT2S")
class AuthFlowIT extends ApiIntegrationTest {

    // Qualified by name: the actuator contributes a second RequestMappingHandlerMapping
    // (controllerEndpointHandlerMapping) and only the MVC one carries the application's routes.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    /** 1.2 — POST /api/auth/register with a new email returns 201 and creates exactly one users row. */
    @Test
    @DisplayName("1.2 registering a new email returns 201 and creates exactly one users row")
    void registersNewUser() {
        String email = uniqueEmail("fresh");

        ResponseEntity<String> response = register(email, FIXTURE_PASSWORD, "Fresh User");

        assertThat(response.getStatusCode().value())
                .as("POST /api/auth/register with a new email")
                .isEqualTo(201);

        JsonNode created = json(response);
        assertThat(created.path("id").asText()).as("UserResponse.id").isNotBlank();
        assertThat(created.path("email").asText()).as("UserResponse.email").isEqualTo(email);
        assertThat(created.path("fullName").asText()).as("UserResponse.fullName").isEqualTo("Fresh User");

        Integer rows = jdbc().queryForObject(
                "select count(*) from users where lower(email) = lower(?)", Integer.class, email);
        assertThat(rows).as("users rows for %s", email).isEqualTo(1);
    }

    /** 1.3 — registering an email that already exists returns 409 and creates no second row. */
    @Test
    @DisplayName("1.3 registering an existing email returns 409 and creates no second row")
    void rejectsDuplicateEmail() {
        String email = uniqueEmail("taken");
        assertThat(register(email, FIXTURE_PASSWORD, "First Claim").getStatusCode().value())
                .as("first registration")
                .isEqualTo(201);

        ResponseEntity<String> second = register(email, "a-completely-different-password", "Second Claim");

        assertThat(second.getStatusCode().value())
                .as("second registration of %s", email)
                .isEqualTo(409);
        Integer rows = jdbc().queryForObject(
                "select count(*) from users where lower(email) = lower(?)", Integer.class, email);
        assertThat(rows).as("users rows for %s after the duplicate attempt", email).isEqualTo(1);
    }

    /**
     * 1.3, in the shape the schema makes contractual: {@code users_email_lower_key} in
     * V1__foundation.sql makes {@code Bookly@x.com} and {@code bookly@x.com} one account, so a
     * case-variant registration is a duplicate and must be refused the same way.
     */
    @Test
    @DisplayName("1.3 an email differing only in case is the same account")
    void rejectsDuplicateEmailDifferingOnlyInCase() {
        String email = uniqueEmail("case");
        assertThat(register(email, FIXTURE_PASSWORD, "Lower Case").getStatusCode().value())
                .isEqualTo(201);

        ResponseEntity<String> variant =
                register(email.toUpperCase(Locale.ROOT), FIXTURE_PASSWORD, "Upper Case");

        assertThat(variant.getStatusCode().value())
                .as("registering %s when %s exists", email.toUpperCase(Locale.ROOT), email)
                .isEqualTo(409);
        Integer rows = jdbc().queryForObject(
                "select count(*) from users where lower(email) = lower(?)", Integer.class, email);
        assertThat(rows).as("users rows for %s ignoring case", email).isEqualTo(1);
    }

    /** 1.4 — POST /api/auth/login with correct credentials returns an access token and a refresh token. */
    @Test
    @DisplayName("1.4 login with correct credentials returns an access and a refresh token")
    void loginReturnsTokenPair() {
        String email = uniqueEmail("login");
        assertThat(register(email, FIXTURE_PASSWORD, "Login User").getStatusCode().value()).isEqualTo(201);

        ResponseEntity<String> response = login(email, FIXTURE_PASSWORD);

        assertThat(response.getStatusCode().value()).as("POST /api/auth/login").isEqualTo(200);
        JsonNode pair = json(response);
        assertThat(pair.path("accessToken").asText()).as("TokenPairResponse.accessToken").isNotBlank();
        assertThat(pair.path("refreshToken").asText()).as("TokenPairResponse.refreshToken").isNotBlank();
        assertThat(pair.path("accessToken").asText())
                .as("the access token is not the refresh token")
                .isNotEqualTo(pair.path("refreshToken").asText());
        assertThat(pair.path("expiresInSeconds").asLong())
                .as("TokenPairResponse.expiresInSeconds")
                .isPositive();
        assertThat(response.getBody())
                .as("no token response may echo the password back")
                .doesNotContain(FIXTURE_PASSWORD);
    }

    /**
     * 1.5 — login with a wrong password returns 401, and the response body is byte-identical to the
     * body returned for an unknown email.
     */
    @Test
    @DisplayName("1.5 a login failure does not reveal whether the account exists")
    void loginFailureDoesNotRevealAccountExistence() {
        String email = uniqueEmail("real");
        assertThat(register(email, FIXTURE_PASSWORD, "Real User").getStatusCode().value()).isEqualTo(201);

        ResponseEntity<String> wrongPassword = login(email, "this-is-not-the-password");
        ResponseEntity<String> unknownEmail = login(uniqueEmail("ghost"), "this-is-not-the-password");

        assertThat(wrongPassword.getStatusCode().value()).as("wrong password").isEqualTo(401);
        assertThat(unknownEmail.getStatusCode().value()).as("unknown email").isEqualTo(401);
        assertThat(wrongPassword.getBody())
                .as("the wrong-password body must be byte-identical to the unknown-email body")
                .isEqualTo(unknownEmail.getBody());
        assertThat(wrongPassword.getHeaders().getContentType())
                .as("content type must not distinguish the two failures either")
                .isEqualTo(unknownEmail.getHeaders().getContentType());
        assertThat(String.valueOf(wrongPassword.getBody()).toLowerCase(Locale.ROOT))
                .as("the failure body must not name the account")
                .doesNotContain(email.toLowerCase(Locale.ROOT));
    }

    /** 1.8 — an expired access token is rejected with 401 on every authenticated route. */
    @Test
    @DisplayName("1.8 an expired access token is rejected with 401 on every authenticated route")
    void expiredAccessTokenRejected() throws InterruptedException {
        Account account = newAccount("expiry");
        String accessToken = account.accessToken();

        // The class-level access-token-ttl is PT2S; wait past it plus a margin.
        Thread.sleep(4_000);

        List<Routes.Route> routes = Routes.authenticated(handlerMapping);
        assertThat(routes)
                .as("there must be authenticated routes to check; none discovered means the "
                        + "criterion cannot be decided")
                .isNotEmpty();

        SoftAssertions soft = new SoftAssertions();
        for (Routes.Route route : routes) {
            String path = Routes.fill(route.pattern(), java.util.UUID.randomUUID().toString());
            Object requestBody = route.method() == HttpMethod.GET ? null : body();
            ResponseEntity<String> response = send(route.method(), path, requestBody, accessToken);
            soft.assertThat(response.getStatusCode().value())
                    .as("%s with an access token that expired 4s ago (if this is 200, either the "
                            + "token never expired or the route is unauthenticated)", route)
                    .isEqualTo(401);
        }
        soft.assertAll();
    }
}
