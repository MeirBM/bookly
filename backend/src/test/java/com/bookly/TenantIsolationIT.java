package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bookly.support.ApiIntegrationTest;
import com.bookly.support.Routes;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Turn-1 criteria 1.10, 1.11 and 1.12 — the suite the whole turn exists for.
 *
 * <p>Everything here goes over HTTP. Pitfall 1: {@code @PreAuthorize} is applied by a proxy, so a
 * test that called the service directly would pass while the route itself was wide open.
 *
 * <p>The route list is read from {@code RequestMappingHandlerMapping} at runtime rather than
 * written out by hand (spec part 4), so a tenant-scoped route added later and forgotten here fails
 * the build instead of passing unnoticed.
 */
class TenantIsolationIT extends ApiIntegrationTest {

    // Qualified by name: the actuator contributes a second RequestMappingHandlerMapping
    // (controllerEndpointHandlerMapping) and only the MVC one carries the application's routes.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    /**
     * 1.10 — a user authenticated against Business A receives 403 on every tenant-scoped route of
     * Business B. One case per registered route.
     *
     * <p>The set is every route under {@code /api/} that is not on {@link Routes#PUBLIC_PATTERNS},
     * not merely those whose path contains {@code {businessId}}. A route that names its resource
     * some other way — {@code /api/appointments/{appointmentId}} — is the same defect wearing a
     * different path, and selecting on {@code {businessId}} would generate no case for it at all.
     *
     * <p>Two case shapes, chosen structurally so neither can be skipped by omission:
     * a route carrying a path variable is fired at a resource the caller has no claim to and must
     * answer 403; a route carrying none cannot be addressed at another tenant's resource, so it is
     * fired as the outsider and must disclose nothing about the other business.
     */
    @TestFactory
    @DisplayName("1.10 a member of business A is forbidden on every tenant-scoped route of business B")
    List<DynamicTest> outsiderIsForbiddenOnEveryTenantScopedRoute() {
        Account outsider = newAccount("outsider");
        newBusiness(outsider, "Outsider Own Business");

        Account owner = newAccount("owner");
        JsonNode otherBusiness = newBusiness(owner, "Someone Else Business");
        String otherBusinessId = otherBusiness.path("id").asText();
        String otherBusinessSlug = otherBusiness.path("slug").asText();

        List<Routes.Route> mustBeCovered = Routes.requiringIsolationCoverage(handlerMapping);
        assertThat(mustBeCovered)
                .as("every route under /api/ that is not on the public allowlist %s; an empty set "
                        + "means the criterion has nothing to decide", Routes.PUBLIC_PATTERNS)
                .isNotEmpty();

        List<DynamicTest> cases = new ArrayList<>();
        for (Routes.Route route : mustBeCovered) {
            if (route.addressableByResourceId()) {
                cases.add(dynamicTest(route + " [foreign id must be 403]", () -> {
                    String path = Routes.fill(route.pattern(), otherBusinessId);
                    Object requestBody = route.method() == HttpMethod.GET ? null : body();
                    ResponseEntity<String> response =
                            send(route.method(), path, requestBody, outsider.accessToken());

                    assertThat(response.getStatusCode().value())
                            .as("%s addressed at a resource of a business the caller does not "
                                    + "belong to", route)
                            .isEqualTo(403);
                    assertThat(String.valueOf(response.getBody()))
                            .as("the refusal must not leak the business it refused")
                            .doesNotContain("Someone Else Business", otherBusinessSlug);
                }));
            } else {
                cases.add(dynamicTest(route + " [must disclose no other tenant]", () -> {
                    Object requestBody = route.method() == HttpMethod.GET ? null : body();
                    ResponseEntity<String> response =
                            send(route.method(), route.pattern(), requestBody, outsider.accessToken());

                    assertThat(response.getStatusCode().value())
                            .as("%s as an authenticated outsider must not fail with a server error", route)
                            .isBetween(200, 499);
                    assertThat(String.valueOf(response.getBody()))
                            .as("%s must disclose nothing about a business the caller does not "
                                    + "belong to", route)
                            .doesNotContain(otherBusinessId, otherBusinessSlug, "Someone Else Business");
                }));
            }
        }

        assertThat(cases)
                .as("one case per route requiring coverage, so none can be skipped by omission")
                .hasSameSizeAs(mustBeCovered);
        return cases;
    }

    /**
     * 1.11 — a businessId supplied in a request body never affects which business is read or
     * written. Tenant access is decided from the caller's business_members row, never from the
     * body.
     */
    @Test
    @DisplayName("1.11 a businessId in the request body is ignored")
    void bodyBusinessIdIsIgnored() {
        Account victim = newAccount("victim");
        JsonNode victimBusiness = newBusiness(victim, "Victim Salon");
        String victimId = victimBusiness.path("id").asText();
        String victimSlug = victimBusiness.path("slug").asText();

        Account attacker = newAccount("attacker");

        ResponseEntity<String> created = post(
                "/api/businesses",
                body(
                        "name", "Attacker Studio",
                        "timezone", "Europe/London",
                        "id", victimId,
                        "businessId", victimId,
                        "slug", victimSlug),
                attacker.accessToken());

        assertThat(created.getStatusCode().value())
                .as("creating a business while claiming another business's id in the body")
                .isEqualTo(201);
        JsonNode result = json(created);
        assertThat(result.path("id").asText())
                .as("the body-supplied id must not become the created business's id")
                .isNotEqualTo(victimId);
        assertThat(result.path("slug").asText())
                .as("the body-supplied slug must not be honoured either")
                .isNotEqualTo(victimSlug);

        // The victim's business is untouched, and the attacker did not become a member of it.
        String storedName = jdbc().queryForObject(
                "select name from businesses where id = ?::uuid", String.class, victimId);
        assertThat(storedName).as("the victim's business name").isEqualTo("Victim Salon");
        Integer memberships = jdbc().queryForObject(
                "select count(*) from business_members where business_id = ?::uuid and user_id = ?::uuid",
                Integer.class,
                victimId,
                attacker.userId());
        assertThat(memberships)
                .as("memberships the attacker gained in the victim's business")
                .isZero();

        // And reading is decided by the path, not by anything the caller says about itself.
        assertThat(get("/api/businesses/" + victimId, attacker.accessToken()).getStatusCode().value())
                .as("the attacker reading the victim's business")
                .isEqualTo(403);
    }

    /**
     * 1.12 — a request for a business that does not exist and a request for a business the caller
     * is not a member of return responses that cannot be distinguished.
     *
     * <p>Pitfall 2: 404-for-absent and 403-for-forbidden is the natural thing to write, and it
     * hands an attacker an oracle for which business ids are real.
     */
    @Test
    @DisplayName("1.12 an absent business and a forbidden one are indistinguishable")
    void absentAndForbiddenAreIndistinguishable() {
        Account caller = newAccount("prober");
        newBusiness(caller, "Prober's Own Business");

        Account other = newAccount("stranger");
        String existingButForbidden = newBusiness(other, "Stranger Salon").path("id").asText();
        String absent = UUID.randomUUID().toString();

        ResponseEntity<String> forbidden = get("/api/businesses/" + existingButForbidden, caller.accessToken());
        ResponseEntity<String> missing = get("/api/businesses/" + absent, caller.accessToken());

        assertThat(missing.getStatusCode())
                .as("the status for an absent business must equal the status for a forbidden one")
                .isEqualTo(forbidden.getStatusCode());
        assertThat(missing.getBody())
                .as("the body for an absent business must be byte-identical to the forbidden one")
                .isEqualTo(forbidden.getBody());
        // 1.12 says status, headers and body bytes. Date is excluded because it is a clock
        // reading, not a fact about the business; response timing is explicitly out of scope.
        java.util.Map<String, java.util.List<String>> missingHeaders = new java.util.TreeMap<>(missing.getHeaders());
        java.util.Map<String, java.util.List<String>> forbiddenHeaders =
                new java.util.TreeMap<>(forbidden.getHeaders());
        missingHeaders.remove("Date");
        forbiddenHeaders.remove("Date");
        assertThat(missingHeaders)
                .as("the headers must not distinguish an absent business from a forbidden one")
                .isEqualTo(forbiddenHeaders);
        assertThat(forbidden.getStatusCode().value())
                .as("and the shared answer is 403, per spec part 3 and criterion 1.10")
                .isEqualTo(403);
    }
}
