package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import com.bookly.support.Routes;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Turn-1 criterion 1.16: the OpenAPI document at /v3/api-docs lists every route added in this turn
 * with its response codes.
 *
 * <p>Turn-1 criterion 1.20: the document declares how a caller authenticates, not only each route's
 * status codes. A client that has to guess the token transport is a client that will guess wrong.
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
}
