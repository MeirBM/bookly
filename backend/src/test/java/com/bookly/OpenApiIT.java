package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import com.bookly.support.Routes;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Turn-1 criterion 1.16: the OpenAPI document at /v3/api-docs lists every route added in this turn
 * with its response codes.
 *
 * <p>Turn-1 criterion 1.20: the document declares how a caller authenticates, not only each route's
 * status codes. A client that has to guess the token transport is a client that will guess wrong.
 *
 * <p>Turn-2 criterion 2.26: the status an operation declares is the status it actually returns. A
 * document that says 200 where the code returns 201 describes an API that does not exist, and a
 * client generated from it is wrong before anyone writes a line of it.
 *
 * <p>The expected list is the set of routes the application registers, read from Spring at runtime,
 * so a route added without documentation fails here rather than being noticed by a client.
 */
class OpenApiIT extends ApiIntegrationTest {

    // Qualified by name: the actuator contributes a second RequestMappingHandlerMapping
    // (controllerEndpointHandlerMapping) and only the MVC one carries the application's routes.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("1.16 the OpenAPI document lists every route with its response codes")
    void documentsEveryRoute() {
        ResponseEntity<String> response = get("/v3/api-docs", null);
        assertThat(response.getStatusCode().value())
                .as("GET /v3/api-docs must be reachable")
                .isEqualTo(200);

        JsonNode document = json(response);
        JsonNode paths = document.path("paths");
        assertThat(paths.isObject()).as("the document must have a paths object").isTrue();

        List<Routes.Route> registered = Routes.application(handlerMapping);
        assertThat(registered).as("routes registered under /api/").isNotEmpty();

        List<String> undocumented = new ArrayList<>();
        List<String> withoutResponses = new ArrayList<>();
        List<String> withoutSuccess = new ArrayList<>();

        for (Routes.Route route : registered) {
            JsonNode operation =
                    paths.path(route.pattern()).path(route.method().name().toLowerCase(Locale.ROOT));
            if (operation.isMissingNode() || operation.isNull()) {
                undocumented.add(route.toString());
                continue;
            }
            JsonNode responses = operation.path("responses");
            if (!responses.isObject() || responses.isEmpty()) {
                withoutResponses.add(route.toString());
                continue;
            }
            boolean success = false;
            for (Iterator<String> codes = responses.fieldNames(); codes.hasNext(); ) {
                if (codes.next().startsWith("2")) {
                    success = true;
                }
            }
            if (!success) {
                withoutSuccess.add(route.toString());
            }
        }

        assertThat(undocumented).as("routes missing from the OpenAPI document").isEmpty();
        assertThat(withoutResponses).as("documented routes with no response codes").isEmpty();
        assertThat(withoutSuccess).as("documented routes with no success response code").isEmpty();
    }

    /**
     * 1.20 — the document declares how a caller authenticates, and the routes that require
     * authentication say so.
     *
     * <p>The scheme asserted here is the one in the committed contract,
     * {@code docs/api/turn-1-openapi.json}: an HTTP bearer scheme carrying a JWT. Whether a route
     * requires it is read as OpenAPI defines it — the operation's own {@code security} if it has
     * one, otherwise the document-level {@code security} — so declaring it globally and declaring
     * it per operation are both accepted, and only the outcome is asserted.
     */
    @Test
    @DisplayName("1.20 the document declares the bearer scheme and the routes that require it")
    void documentsHowACallerAuthenticates() {
        ResponseEntity<String> response = get("/v3/api-docs", null);
        assertThat(response.getStatusCode().value()).as("GET /v3/api-docs").isEqualTo(200);
        JsonNode document = json(response);

        // ---- a bearer/JWT scheme is declared
        JsonNode schemes = document.path("components").path("securitySchemes");
        assertThat(schemes.isObject() && !schemes.isEmpty())
                .as("components.securitySchemes must declare how a caller authenticates")
                .isTrue();

        List<String> bearerSchemes = new ArrayList<>();
        for (Iterator<String> names = schemes.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            JsonNode scheme = schemes.path(name);
            if ("http".equalsIgnoreCase(scheme.path("type").asText())
                    && "bearer".equalsIgnoreCase(scheme.path("scheme").asText())) {
                bearerSchemes.add(name);
                assertThat(scheme.path("bearerFormat").asText())
                        .as("securitySchemes.%s.bearerFormat — the committed contract says JWT", name)
                        .isEqualTo("JWT");
            }
        }
        assertThat(bearerSchemes)
                .as("an HTTP bearer scheme, as the committed contract declares; the API serves "
                        + "clients with no cookie jar, so the transport has to be a header")
                .isNotEmpty();

        // ---- every route that needs a token references it, and the auth routes do not
        List<Routes.Route> registered = Routes.application(handlerMapping);
        assertThat(registered).as("routes registered under /api/").isNotEmpty();

        List<String> unprotectedInDocument = new ArrayList<>();
        List<String> authRoutesDemandingAToken = new ArrayList<>();
        List<String> referencingAnUndeclaredScheme = new ArrayList<>();

        for (Routes.Route route : registered) {
            JsonNode operation = document.path("paths")
                    .path(route.pattern())
                    .path(route.method().name().toLowerCase(Locale.ROOT));
            if (operation.isMissingNode()) {
                continue; // documentsEveryRoute already decides this
            }
            JsonNode security = operation.has("security") ? operation.path("security") : document.path("security");
            List<String> required = new ArrayList<>();
            if (security.isArray()) {
                for (JsonNode requirement : security) {
                    requirement.fieldNames().forEachRemaining(required::add);
                }
            }

            boolean isAuthEndpoint = route.pattern().startsWith("/api/auth/");
            if (isAuthEndpoint) {
                if (!required.isEmpty()) {
                    authRoutesDemandingAToken.add(route + " requires " + required);
                }
                continue;
            }
            if (required.isEmpty()) {
                unprotectedInDocument.add(route.toString());
                continue;
            }
            for (String schemeName : required) {
                if (!schemes.has(schemeName)) {
                    referencingAnUndeclaredScheme.add(route + " -> " + schemeName);
                }
            }
            assertThat(required)
                    .as("%s must be documented as needing the bearer scheme", route)
                    .containsAnyElementsOf(bearerSchemes);
        }

        assertThat(unprotectedInDocument)
                .as("routes under /api/businesses documented as needing no authentication")
                .isEmpty();
        assertThat(authRoutesDemandingAToken)
                .as("auth routes documented as needing a token, which no caller can have yet")
                .isEmpty();
        assertThat(referencingAnUndeclaredScheme)
                .as("routes referencing a security scheme the document never declares")
                .isEmpty();
    }

    // ------------------------------------------------------------------- 2.26

    /**
     * 2.26 — every operation the document describes is exercised over HTTP, and the status it
     * actually returns must be one the document declares.
     *
     * <p>Nothing here names a route. The operations come from the document; a request is assembled
     * for each from suppliers keyed by <em>parameter name</em> and by <em>request schema</em>, both
     * of which the document itself states. A route added later that reuses those names and schemas
     * is exercised without anyone editing this test, and one that does not is reported by name in
     * the skip list rather than passing quietly.
     *
     * <p>POST is not assumed to mean create: {@code /api/auth/login} and {@code /api/auth/refresh}
     * answer 200 and {@code /api/auth/logout} answers 204, all correctly. The assertion is that the
     * observed status is declared, whatever it is — not that it matches a status this test decided
     * the verb ought to produce.
     */
    @Test
    @DisplayName("2.26 the documented status codes are the ones the operations return")
    void documentedStatusCodesMatchReality() {
        ResponseEntity<String> document = get("/v3/api-docs", null);
        assertThat(document.getStatusCode().value()).as("GET /v3/api-docs").isEqualTo(200);
        JsonNode paths = json(document).path("paths");

        Fixtures fixtures = new Fixtures();

        List<String> undeclared = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> exercised = new ArrayList<>();

        for (Iterator<String> pathNames = paths.fieldNames(); pathNames.hasNext(); ) {
            String template = pathNames.next();
            JsonNode operations = paths.path(template);
            for (Iterator<String> methods = operations.fieldNames(); methods.hasNext(); ) {
                String method = methods.next();
                JsonNode operation = operations.path(method);
                String label = method.toUpperCase(Locale.ROOT) + " " + template;

                Request request;
                try {
                    request = fixtures.buildRequest(template, operation);
                } catch (CannotExercise e) {
                    skipped.add(label + " (" + e.getMessage() + ")");
                    continue;
                }

                ResponseEntity<String> response =
                        send(HttpMethod.valueOf(method.toUpperCase(Locale.ROOT)), request.path(), request.body(),
                                request.token());
                String observed = String.valueOf(response.getStatusCode().value());

                List<String> declared = new ArrayList<>();
                operation.path("responses").fieldNames().forEachRemaining(declared::add);
                exercised.add(label + " -> " + observed);
                if (!declared.contains(observed)) {
                    undeclared.add(label + " returned " + observed + " but declares " + declared
                            + " (body: " + abbreviate(response.getBody()) + ")");
                }
            }
        }

        assertThat(undeclared)
                .as("operations whose real status the document does not declare")
                .isEmpty();
        assertThat(exercised)
                .as("no operation was exercised at all, so nothing was decided; skipped: %s", skipped)
                .isNotEmpty();

        // 2.26 is about every operation, so an operation this test cannot call is an operation
        // nothing checks. Like the route table, this is the guardrail asking for a case: teach
        // Fixtures the new parameter name or request schema and the new operation is covered.
        assertThat(skipped)
                .as("operations the document describes that this test could not call, and which "
                        + "are therefore checked by nothing")
                .isEmpty();
    }

    private static String abbreviate(String body) {
        String text = String.valueOf(body);
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    /** A request assembled from what the document says about an operation. */
    private record Request(String path, Object body, String token) {}

    /** Raised when the document describes something this test has no way to call. */
    private static final class CannotExercise extends RuntimeException {
        CannotExercise(String reason) {
            super(reason);
        }
    }

    /**
     * Values for parameters and bodies, keyed by the names the document uses. Each path-variable
     * supplier creates a <em>fresh</em> resource, so a DELETE removes something no other operation
     * needed, and order between operations never matters.
     */
    private final class Fixtures {

        private final Account owner = newAccount("openapi-real");
        private final String businessId =
                newBusiness(owner, "Status Code Salon", "Asia/Jerusalem").path("id").asText();

        private String freshServiceId() {
            return newService(owner, businessId, "Service " + UUID.randomUUID(), 30).path("id").asText();
        }

        private String freshEmployeeId() {
            return newEmployee(owner, businessId, "Employee " + UUID.randomUUID()).path("id").asText();
        }

        private String pathValue(String name) {
            return switch (name) {
                case "businessId" -> businessId;
                case "serviceId" -> freshServiceId();
                case "employeeId" -> freshEmployeeId();
                case "workingHoursId" -> newWorkingHours(
                                owner, businessId, freshEmployeeId(), DayOfWeek.MONDAY, "09:00:00", "17:00:00")
                        .path("id")
                        .asText();
                case "blockedTimeId" -> {
                    Instant start = Instant.parse("2026-06-10T07:00:00Z");
                    yield newBlockedTime(owner, businessId, null, start, start.plusSeconds(3600), "Blocked")
                            .path("id")
                            .asText();
                }
                default -> throw new CannotExercise("no value known for path variable {" + name + "}");
            };
        }

        private String queryValue(String name) {
            return switch (name) {
                case "serviceId" -> freshServiceId();
                case "employeeId" -> freshEmployeeId();
                case "date" -> "2026-06-10";
                default -> throw new CannotExercise("no value known for query parameter " + name);
            };
        }

        private Object bodyFor(String schema) {
            return switch (schema) {
                case "RegisterRequest" -> body(
                        "email", uniqueEmail("openapi"), "password", FIXTURE_PASSWORD, "fullName", "Doc Reader");
                case "LoginRequest" -> body("email", owner.email(), "password", owner.password());
                case "RefreshRequest" -> body("refreshToken", freshRefreshToken());
                case "CreateBusinessRequest" -> body("name", "Doc Business " + UUID.randomUUID(),
                        "timezone", "Asia/Jerusalem");
                case "CreateService" -> body("name", "Doc Service " + UUID.randomUUID(),
                        "durationMinutes", 30, "priceMinor", 1000L);
                case "CreateEmployee" -> body("fullName", "Doc Employee " + UUID.randomUUID());
                case "CreateWorkingHours" -> body("weekday", "TUESDAY", "startsAt", "09:00:00", "endsAt", "17:00:00");
                case "CreateBlockedTime" -> body("startsAt", "2026-06-11T07:00:00Z",
                        "endsAt", "2026-06-11T08:00:00Z", "reason", "Documented");
                case "SetServices" -> body("serviceIds", List.of(freshServiceId()));
                default -> throw new CannotExercise("no sample body known for schema " + schema);
            };
        }

        /** A refresh token that has not been used, so refresh and logout both answer normally. */
        private String freshRefreshToken() {
            ResponseEntity<String> loggedIn = login(owner.email(), owner.password());
            assertThat(loggedIn.getStatusCode().value()).as("fixture login").isEqualTo(200);
            return json(loggedIn).path("refreshToken").asText();
        }

        Request buildRequest(String template, JsonNode operation) {
            String path = template;
            java.util.regex.Matcher variables =
                    java.util.regex.Pattern.compile("\\{([^/}]+)}").matcher(template);
            while (variables.find()) {
                path = path.replace("{" + variables.group(1) + "}", pathValue(variables.group(1)));
            }

            StringBuilder query = new StringBuilder();
            for (JsonNode parameter : operation.path("parameters")) {
                if ("query".equals(parameter.path("in").asText()) && parameter.path("required").asBoolean()) {
                    query.append(query.isEmpty() ? "?" : "&")
                            .append(parameter.path("name").asText())
                            .append("=")
                            .append(queryValue(parameter.path("name").asText()));
                }
            }

            Object requestBody = null;
            JsonNode content = operation.path("requestBody").path("content");
            for (Iterator<String> mediaTypes = content.fieldNames(); mediaTypes.hasNext(); ) {
                String ref = content.path(mediaTypes.next()).path("schema").path("$ref").asText("");
                if (!ref.isBlank()) {
                    requestBody = bodyFor(ref.substring(ref.lastIndexOf('/') + 1));
                    break;
                }
            }

            return new Request(path + query, requestBody, owner.accessToken());
        }
    }
}
