package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bookly.support.ApiIntegrationTest;
import com.bookly.support.Routes;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
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
                .as("every route Routes classifies as %s; the unauthenticated entry points %s and "
                        + "the public surface under %s carry their own expectations elsewhere. An "
                        + "empty set here means the criterion has nothing to decide",
                        Routes.Category.TENANT_SCOPED,
                        Routes.UNAUTHENTICATED_ENTRY_PATTERNS,
                        Routes.PUBLIC_PREFIX)
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

    /**
     * 2.18 — a resource belonging to another business cannot be read, modified or deleted, even by
     * id, and even by a caller who is a perfectly legitimate member of the business named in the
     * path.
     *
     * <p>Pitfall 7 is the whole of it: the tenant guard proves the caller belongs to business A. It
     * does not prove that employee {@code x} does. {@code GET /businesses/{a}/employees/{x}} passes
     * the guard while reaching into business B, so every lookup has to filter on both — the guard
     * is the gate, the filter is the depth behind it.
     *
     * <p>Each foreign id is asserted to be answered exactly as an id that never existed. That is
     * turn 1's criterion 1.12 applied one level down: if "belongs to someone else" and "does not
     * exist" are distinguishable, the id space becomes an oracle for what other businesses own.
     */
    @Test
    @DisplayName("2.18 a resource of another business is unreachable by id")
    void crossTenantResourceIsRefused() {
        Account mine = newAccount("mine");
        String myBusiness = newBusiness(mine, "My Salon", "Asia/Jerusalem").path("id").asText();
        String myService = newService(mine, myBusiness, "My Service", 30).path("id").asText();

        Account theirs = newAccount("theirs");
        String theirBusiness = newBusiness(theirs, "Their Salon", "Asia/Jerusalem").path("id").asText();
        String theirService = newService(theirs, theirBusiness, "Their Service", 30).path("id").asText();
        String theirEmployee = newEmployee(theirs, theirBusiness, "Their Employee").path("id").asText();
        linkServices(theirs, theirBusiness, theirEmployee, theirService);
        String theirWorkingHours = newWorkingHours(
                        theirs, theirBusiness, theirEmployee, java.time.DayOfWeek.MONDAY, "09:00:00", "17:00:00")
                .path("id")
                .asText();
        java.time.Instant blockStart = java.time.LocalDate.of(2026, 6, 10)
                .atTime(10, 0)
                .atZone(java.time.ZoneId.of("Asia/Jerusalem"))
                .toInstant();
        String theirBlockedTime = newBlockedTime(
                        theirs, theirBusiness, theirEmployee, blockStart, blockStart.plusSeconds(3600), "Theirs")
                .path("id")
                .asText();

        // Every attempt is made through *my* business, which I am entitled to, at *their* row.
        String base = "/api/businesses/" + myBusiness;
        record Attempt(String what, HttpMethod method, String pathWithForeignId, String pathWithUnknownId, Object body) {}
        String unknown = UUID.randomUUID().toString();
        List<Attempt> attempts = List.of(
                new Attempt("read another business's working hours",
                        HttpMethod.GET,
                        base + "/employees/" + theirEmployee + "/working-hours",
                        base + "/employees/" + unknown + "/working-hours",
                        null),
                new Attempt("relink another business's employee",
                        HttpMethod.PUT,
                        base + "/employees/" + theirEmployee + "/services",
                        base + "/employees/" + unknown + "/services",
                        body("serviceIds", List.of(myService))),
                new Attempt("delete another business's employee",
                        HttpMethod.DELETE,
                        base + "/employees/" + theirEmployee,
                        base + "/employees/" + unknown,
                        null),
                new Attempt("delete another business's service",
                        HttpMethod.DELETE,
                        base + "/services/" + theirService,
                        base + "/services/" + unknown,
                        null),
                new Attempt("delete another business's working hours",
                        HttpMethod.DELETE,
                        base + "/working-hours/" + theirWorkingHours,
                        base + "/working-hours/" + unknown,
                        null),
                new Attempt("delete another business's blocked time",
                        HttpMethod.DELETE,
                        base + "/blocked-times/" + theirBlockedTime,
                        base + "/blocked-times/" + unknown,
                        null));

        SoftAssertions soft = new SoftAssertions();
        for (Attempt attempt : attempts) {
            ResponseEntity<String> foreign =
                    send(attempt.method(), attempt.pathWithForeignId(), attempt.body(), mine.accessToken());
            ResponseEntity<String> unknownId =
                    send(attempt.method(), attempt.pathWithUnknownId(), attempt.body(), mine.accessToken());

            soft.assertThat(foreign.getStatusCode().is2xxSuccessful())
                    .as("%s (%s %s) must not succeed", attempt.what(), attempt.method(), attempt.pathWithForeignId())
                    .isFalse();
            soft.assertThat(foreign.getStatusCode())
                    .as("%s: a row owned by someone else must answer exactly as one that never "
                            + "existed, or the id space tells an attacker what is real", attempt.what())
                    .isEqualTo(unknownId.getStatusCode());
            soft.assertThat(foreign.getBody())
                    .as("%s: same body for the foreign id and the unknown id", attempt.what())
                    .isEqualTo(unknownId.getBody());
            soft.assertThat(String.valueOf(foreign.getBody()))
                    .as("%s: the refusal must not echo what it refused", attempt.what())
                    .doesNotContain("Their Employee", "Their Service");
        }
        soft.assertAll();

        // And nothing was actually touched: the owner still sees everything they had.
        assertThat(json(get("/api/businesses/" + theirBusiness + "/employees", theirs.accessToken())).toString())
                .as("their employee survived every attempt")
                .contains(theirEmployee);
        assertThat(json(get("/api/businesses/" + theirBusiness + "/services", theirs.accessToken())).toString())
                .as("their service survived every attempt")
                .contains(theirService);
        assertThat(json(get("/api/businesses/" + theirBusiness + "/blocked-times", theirs.accessToken())).toString())
                .as("their blocked time survived every attempt")
                .contains(theirBlockedTime);
        assertThat(json(get("/api/businesses/" + theirBusiness + "/employees/" + theirEmployee
                        + "/working-hours", theirs.accessToken())).toString())
                .as("their working hours survived every attempt")
                .contains(theirWorkingHours);
        Integer stillLinked = jdbc().queryForObject(
                "select count(*) from employee_services where employee_id = ?::uuid and service_id = ?::uuid",
                Integer.class,
                theirEmployee,
                theirService);
        assertThat(stillLinked).as("their employee-service link was not rewritten from outside").isEqualTo(1);
    }
}
