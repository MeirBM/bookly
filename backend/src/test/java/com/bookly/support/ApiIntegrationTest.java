package com.bookly.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
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
        return newBusiness(owner, name, "Europe/London");
    }

    protected JsonNode newBusiness(Account owner, String name, String timezone) {
        ResponseEntity<String> created =
                post("/api/businesses", body("name", name, "timezone", timezone), owner.accessToken());
        if (created.getStatusCode().value() != 201) {
            throw new AssertionError("fixture setup: create business expected 201 but got "
                    + created.getStatusCode() + " body=" + created.getBody());
        }
        return json(created);
    }

    // ------------------------------------------------- turn-2 configuration fixtures

    /** Fails loudly rather than returning a body no assertion will understand. */
    private JsonNode created(ResponseEntity<String> response, String what) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new AssertionError("fixture setup: " + what + " expected 2xx but got "
                    + response.getStatusCode() + " body=" + response.getBody());
        }
        return json(response);
    }

    protected String businessPath(String businessId, String suffix) {
        return "/api/businesses/" + businessId + suffix;
    }

    protected JsonNode newService(Account owner, String businessId, String name, int durationMinutes) {
        return created(
                post(businessPath(businessId, "/services"),
                        body("name", name, "durationMinutes", durationMinutes, "priceMinor", 5000L),
                        owner.accessToken()),
                "create service");
    }

    protected JsonNode newEmployee(Account owner, String businessId, String fullName) {
        return created(
                post(businessPath(businessId, "/employees"), body("fullName", fullName), owner.accessToken()),
                "create employee");
    }

    protected void linkServices(Account owner, String businessId, String employeeId, String... serviceIds) {
        ResponseEntity<String> response = send(
                org.springframework.http.HttpMethod.PUT,
                businessPath(businessId, "/employees/" + employeeId + "/services"),
                body("serviceIds", java.util.List.of(serviceIds)),
                owner.accessToken());
        created(response, "link employee to services");
    }

    protected JsonNode newWorkingHours(
            Account owner, String businessId, String employeeId, DayOfWeek weekday, String start, String end) {
        return created(
                post(businessPath(businessId, "/employees/" + employeeId + "/working-hours"),
                        body("weekday", weekday.name(), "startsAt", start, "endsAt", end),
                        owner.accessToken()),
                "create working hours");
    }

    protected JsonNode newBlockedTime(
            Account owner, String businessId, String employeeId, Instant start, Instant end, String reason) {
        Map<String, Object> payload = body("startsAt", start.toString(), "endsAt", end.toString(), "reason", reason);
        if (employeeId != null) {
            payload.put("employeeId", employeeId);
        }
        return created(
                post(businessPath(businessId, "/blocked-times"), payload, owner.accessToken()),
                "create blocked time");
    }

    protected ResponseEntity<String> availability(
            Account caller, String businessId, String serviceId, String employeeId, LocalDate date) {
        StringBuilder path = new StringBuilder(businessPath(businessId, "/availability"))
                .append("?serviceId=").append(serviceId)
                .append("&date=").append(date);
        if (employeeId != null) {
            path.append("&employeeId=").append(employeeId);
        }
        return get(path.toString(), caller.accessToken());
    }

    // ------------------------------------------------------ turn-3 booking fixtures

    /** A business configured far enough that something can actually be booked in it. */
    public record Bookable(
            Account owner,
            String businessId,
            String slug,
            String serviceId,
            String employeeId,
            int durationMinutes,
            String timezone) {}

    /**
     * The day the booking suites work on: the first Wednesday at least three weeks from now.
     *
     * <p>Relative to now, not a fixed calendar date. A fixed one was a time bomb: criterion 3.28
     * refuses a start in the past with 400, so the morning after that date passed, every booking
     * test in the project would have failed — and failed looking like a booking defect rather than
     * a stale fixture, which is the most expensive kind of red build to read.
     *
     * <p>Three weeks ahead keeps it comfortably inside the booking horizon, including the narrowed
     * horizon {@code BookingIT} sets for itself. The weekday is pinned deliberately rather than
     * left to whatever "now plus 21 days" lands on, so a fixture that seeds hours for one named
     * weekday still lines up with it.
     */
    protected static final LocalDate BOOKING_DATE = LocalDate.now(java.time.ZoneOffset.UTC)
            .plusDays(21)
            .with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));

    /**
     * Business, service, employee, the link between them, and hours on every weekday — so a test
     * never has to reason about which day {@link #BOOKING_DATE} lands on.
     */
    protected Bookable newBookableBusiness(String label, int durationMinutes) {
        Account owner = newAccount(label);
        JsonNode business = newBusiness(owner, "Bookable " + label + " " + UUID.randomUUID(), "Asia/Jerusalem");
        String businessId = business.path("id").asText();
        String serviceId = newService(owner, businessId, "Service " + UUID.randomUUID(), durationMinutes)
                .path("id")
                .asText();
        String employeeId = newEmployee(owner, businessId, "Employee " + UUID.randomUUID()).path("id").asText();
        linkServices(owner, businessId, employeeId, serviceId);
        for (DayOfWeek weekday : DayOfWeek.values()) {
            newWorkingHours(owner, businessId, employeeId, weekday, "09:00:00", "17:00:00");
        }
        return new Bookable(
                owner,
                businessId,
                business.path("slug").asText(),
                serviceId,
                employeeId,
                durationMinutes,
                business.path("timezone").asText());
    }

    /** Adds a second employee who also performs the service. */
    protected String addLinkedEmployee(Bookable bookable, String name) {
        String employeeId = newEmployee(bookable.owner(), bookable.businessId(), name).path("id").asText();
        linkServices(bookable.owner(), bookable.businessId(), employeeId, bookable.serviceId());
        for (DayOfWeek weekday : DayOfWeek.values()) {
            newWorkingHours(bookable.owner(), bookable.businessId(), employeeId, weekday, "09:00:00", "17:00:00");
        }
        return employeeId;
    }

    protected List<Instant> availableStarts(
            Account caller, String businessId, String serviceId, String employeeId, LocalDate date) {
        ResponseEntity<String> response = availability(caller, businessId, serviceId, employeeId, date);
        if (response.getStatusCode().value() != 200) {
            throw new AssertionError("fixture setup: availability expected 200 but got "
                    + response.getStatusCode() + " body=" + response.getBody());
        }
        List<Instant> starts = new java.util.ArrayList<>();
        json(response).path("slots").forEach(slot -> starts.add(Instant.parse(slot.path("start").asText())));
        return starts;
    }

    /** The first time the engine is currently willing to offer for this service and employee. */
    protected Instant firstAvailableStart(Bookable bookable) {
        List<Instant> starts = availableStarts(
                bookable.owner(), bookable.businessId(), bookable.serviceId(), bookable.employeeId(), BOOKING_DATE);
        if (starts.isEmpty()) {
            throw new AssertionError("fixture setup: no slot on " + BOOKING_DATE + " for " + bookable);
        }
        return starts.get(0);
    }

    protected Map<String, Object> bookingBody(
            String serviceId, String employeeId, Instant startsAt, String customerEmail) {
        return body(
                "serviceId", serviceId,
                "employeeId", employeeId,
                "startsAt", startsAt.toString(),
                "customerName", "Customer " + customerEmail,
                "customerEmail", customerEmail,
                "customerPhone", "+972500000000");
    }

    /** Books through the owner's route. */
    protected ResponseEntity<String> book(
            Bookable bookable, String employeeId, Instant startsAt, String customerEmail) {
        return post(
                businessPath(bookable.businessId(), "/appointments"),
                bookingBody(bookable.serviceId(), employeeId, startsAt, customerEmail),
                bookable.owner().accessToken());
    }

    protected JsonNode bookOrFail(Bookable bookable, Instant startsAt, String customerEmail) {
        ResponseEntity<String> response = book(bookable, bookable.employeeId(), startsAt, customerEmail);
        if (response.getStatusCode().value() != 201) {
            throw new AssertionError("fixture setup: booking expected 201 but got "
                    + response.getStatusCode() + " body=" + response.getBody());
        }
        return json(response);
    }

    // ------------------------------------------------------------- public surface

    protected String publicPath(String slug, String suffix) {
        return "/api/public/businesses/" + slug + suffix;
    }

    /** Anonymous: no token, deliberately. */
    protected ResponseEntity<String> publicBusiness(String slug) {
        return get(publicPath(slug, ""), null);
    }

    protected ResponseEntity<String> publicAvailability(
            String slug, String serviceId, String employeeId, LocalDate date) {
        StringBuilder path = new StringBuilder(publicPath(slug, "/availability"))
                .append("?serviceId=").append(serviceId)
                .append("&date=").append(date);
        if (employeeId != null) {
            path.append("&employeeId=").append(employeeId);
        }
        return get(path.toString(), null);
    }

    protected ResponseEntity<String> publicBook(String slug, Object requestBody) {
        return post(publicPath(slug, "/appointments"), requestBody, null);
    }
}