package com.bookly.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HTTP-driving base for the specification suites.
 *
 * <p>Every assertion in these suites goes over the wire, never through a service bean. Turn-1 spec,
 * pitfall 1: {@code @PreAuthorize} is applied by a proxy, so a direct call from inside the JVM
 * bypasses it entirely and a direct-call test would pass while the real route is unprotected.
 *
 * <p>Written from {@code docs/spec/turn-1.md} and {@code docs/api/turn-1-openapi.json} only; the
 * author of these tests has not read {@code backend/src/main}.
 */
public abstract class ApiIntegrationTest extends AbstractIntegrationTest {

    protected static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected DataSource dataSource;

    protected JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    // ---------------------------------------------------------------- HTTP

    protected ResponseEntity<String> send(HttpMethod method, String path, Object body, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.ALL));
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        HttpEntity<String> entity = new HttpEntity<>(body == null ? null : write(body), headers);
        return rest.exchange(path, method, entity, String.class);
    }

    protected ResponseEntity<String> post(String path, Object body) {
        return send(HttpMethod.POST, path, body, null);
    }

    protected ResponseEntity<String> post(String path, Object body, String accessToken) {
        return send(HttpMethod.POST, path, body, accessToken);
    }

    protected ResponseEntity<String> get(String path, String accessToken) {
        return send(HttpMethod.GET, path, null, accessToken);
    }

    protected static String write(Object value) {
        if (value instanceof String s) {
            return s;
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("could not serialise request body", e);
        }
    }

    protected static JsonNode json(ResponseEntity<String> response) {
        try {
            return JSON.readTree(response.getBody() == null ? "null" : response.getBody());
        } catch (Exception e) {
            throw new AssertionError(
                    "response body was not JSON: status=" + response.getStatusCode() + " body=" + response.getBody(), e);
        }
    }

    // ------------------------------------------------------------- fixtures

    /** A registered, logged-in user. */
    public record Account(String userId, String email, String password, String accessToken, String refreshToken) {}

    protected static Map<String, Object> body(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    protected String uniqueEmail(String label) {
        return label + "-" + UUID.randomUUID() + "@example.test";
    }

    /** The password used by fixtures. Long enough for the 12-character minimum in the contract. */
    protected static final String FIXTURE_PASSWORD = "correct-horse-battery-staple-1";  // allow-secret: test fixture, never a real credential

    protected ResponseEntity<String> register(String email, String password, String fullName) {
        return post("/api/auth/register", body("email", email, "password", password, "fullName", fullName));
    }

    protected ResponseEntity<String> login(String email, String password) {
        return post("/api/auth/login", body("email", email, "password", password));
    }

    /** Registers a fresh user and logs it in. Fails loudly if either step does not do what the contract says. */
    protected Account newAccount(String label) {
        String email = uniqueEmail(label);
        ResponseEntity<String> registered = register(email, FIXTURE_PASSWORD, "Test " + label);
        if (registered.getStatusCode().value() != 201) {
            throw new AssertionError("fixture setup: register expected 201 but got "
                    + registered.getStatusCode() + " body=" + registered.getBody());
        }
        String userId = json(registered).path("id").asText(null);
        ResponseEntity<String> loggedIn = login(email, FIXTURE_PASSWORD);
        if (loggedIn.getStatusCode().value() != 200) {
            throw new AssertionError("fixture setup: login expected 200 but got "
                    + loggedIn.getStatusCode() + " body=" + loggedIn.getBody());
        }
        JsonNode pair = json(loggedIn);
        return new Account(
                userId,
                email,
                FIXTURE_PASSWORD,
                pair.path("accessToken").asText(null),
                pair.path("refreshToken").asText(null));
    }

    /** Creates a business owned by the given account and returns the created representation. */
    protected JsonNode newBusiness(Account owner, String name) {
        ResponseEntity<String> created =
                post("/api/businesses", body("name", name, "timezone", "Europe/London"), owner.accessToken());
        if (created.getStatusCode().value() != 201) {
            throw new AssertionError("fixture setup: create business expected 201 but got "
                    + created.getStatusCode() + " body=" + created.getBody());
        }
        return json(created);
    }
}
