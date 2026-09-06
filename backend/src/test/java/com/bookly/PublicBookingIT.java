package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bookly.support.ApiIntegrationTest;
import com.bookly.support.Routes;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Turn-3 criteria 3.11, 3.12, 3.13, 3.14, 3.16 and 3.17: the surface with no account behind it.
 *
 * <p>This is the only part of the system an anonymous stranger can reach, so it is the only part
 * where a disclosure is a disclosure to everyone. The spec's own reason for a separate controller
 * is that sharing a response shape between an owner's dashboard and a public page is how a
 * customer's phone number ends up in a public JSON body.
 *
 * <p>3.13 and 3.14 are therefore written as an attack rather than as a field check: every public
 * response is collected and the raw bytes are searched for anything a stranger must not have. A
 * test that asserted "the response has no customerEmail field" would pass against a response that
 * spelled it {@code contact} — this one does not care what it is called.
 */
class PublicBookingIT extends ApiIntegrationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    /**
     * 3.11 — everything under the public prefix is reachable without a token. Generated from the
     * route table, so a public route added later is held to this without anyone remembering.
     */
    @TestFactory
    @DisplayName("3.11 an anonymous visitor can reach every public route")
    List<DynamicTest> anonymousVisitorCanReachTheBookingSurface() {
        Bookable bookable = newBookableBusiness("public-reach", 60);
        Instant slot = firstAvailableStart(bookable);

        List<Routes.Route> routes = Routes.publicSurface(handlerMapping);
        assertThat(routes)
                .as("routes under %s; an empty set means the public surface does not exist and "
                        + "criteria 3.11 to 3.17 have nothing to decide", Routes.PUBLIC_PREFIX)
                .isNotEmpty();

        List<DynamicTest> cases = new ArrayList<>(routes.stream()
                .map(route -> dynamicTest(route.toString(), () -> {
                    String path = Routes.fill(route.pattern(), Map.of("slug", bookable.slug()));
                    Object requestBody = route.method() == HttpMethod.GET
                            ? null
                            : bookingBody(bookable.serviceId(), bookable.employeeId(), slot,
                                    UUID.randomUUID() + "@x.test");
                    String query = route.pattern().endsWith("/availability")
                            ? "?serviceId=" + bookable.serviceId() + "&date=" + BOOKING_DATE
                            : "";

                    ResponseEntity<String> response = send(route.method(), path + query, requestBody, null);

                    assertThat(response.getStatusCode().value())
                            .as("%s with no token: the public surface has no account to offer, so "
                                    + "demanding authentication here closes the front door", route)
                            .isNotIn(401, 403);
                    assertThat(response.getStatusCode().value())
                            .as("%s with no token must be answered, not fail", route)
                            .isBetween(200, 299);
                }))
                .toList());

        cases.add(dynamicTest("the public business names its services", () -> {
            JsonNode business = json(publicBusiness(bookable.slug()));
            assertThat(business.path("name").asText()).isNotBlank();
            assertThat(business.path("slug").asText()).isEqualTo(bookable.slug());
            assertThat(business.path("timezone").asText())
                    .as("a visitor is shown the business's clock, not their own")
                    .isEqualTo(bookable.timezone());
            assertThat(business.path("services").isArray() && !business.path("services").isEmpty())
                    .as("a page with no services is a page nobody can book from")
                    .isTrue();
        }));
        cases.add(dynamicTest("the public availability offers the same slots the engine computed", () -> {
            ResponseEntity<String> response =
                    publicAvailability(bookable.slug(), bookable.serviceId(), null, BOOKING_DATE);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(json(response).path("slots").isEmpty())
                    .as("the seeded 09:00-17:00 window offers slots publicly too")
                    .isFalse();
        }));
        return cases;
    }

    /** 3.12 — a visitor with no account can create an appointment. */
    @Test
    @DisplayName("3.12 an anonymous visitor can book")
    void anonymousVisitorCanBook() {
        Bookable bookable = newBookableBusiness("public-book", 60);
        Instant slot = firstAvailableStart(bookable);
        String email = "visitor-" + UUID.randomUUID() + "@example.test";

        ResponseEntity<String> response = publicBook(
                bookable.slug(),
                body(
                        "serviceId", bookable.serviceId(),
                        "employeeId", bookable.employeeId(),
                        "startsAt", slot.toString(),
                        "customerName", "Anonymous Visitor",
                        "customerEmail", email,
                        "customerPhone", "+972501112222"));

        assertThat(response.getStatusCode().value())
                .as("booking without an account is the whole point of the public page")
                .isEqualTo(201);
        JsonNode confirmation = json(response);
        assertThat(confirmation.path("id").asText()).as("the visitor is told what they booked").isNotBlank();
        assertThat(confirmation.path("startsAt").asText()).isEqualTo(slot.toString());
        assertThat(confirmation.path("timezone").asText())
                .as("pitfall 8: the confirmation shows the business's clock")
                .isEqualTo(bookable.timezone());

        Integer stored = jdbc().queryForObject(
                "select count(*) from appointments where business_id = ?::uuid and starts_at = ?",
                Integer.class,
                bookable.businessId(),
                java.sql.Timestamp.from(slot));
        assertThat(stored).as("the appointment exists, not merely the confirmation").isEqualTo(1);
    }

    /**
     * 3.13 — no customer's name, email or phone, and no other appointment's details, anywhere on
     * the public surface.
     *
     * <p>The bookings are made through both write paths, because a leak only has to exist on one of
     * them, and the search is over raw response bytes rather than named fields.
     */
    @Test
    @DisplayName("3.13 the public surface discloses no customer data")
    void publicSurfaceDisclosesNoCustomerData() {
        Bookable bookable = newBookableBusiness("public-privacy", 60);
        List<Instant> slots = availableStarts(bookable.owner(), bookable.businessId(), bookable.serviceId(),
                bookable.employeeId(), BOOKING_DATE);
        assertThat(slots.size()).as("enough slots to seed two bookings").isGreaterThan(4);

        // Two customers whose details are unmistakable if they ever appear anywhere.
        String ownerRouteName = "Wilhelmina Quattrocchi";
        String ownerRouteEmail = "wilhelmina.quattrocchi@example.test";
        String ownerRoutePhone = "+972509876543";
        post(
                businessPath(bookable.businessId(), "/appointments"),
                body(
                        "serviceId", bookable.serviceId(),
                        "employeeId", bookable.employeeId(),
                        "startsAt", slots.get(0).toString(),
                        "customerName", ownerRouteName,
                        "customerEmail", ownerRouteEmail,
                        "customerPhone", ownerRoutePhone),
                bookable.owner().accessToken());

        String publicRouteName = "Bartholomew Featherstonehaugh";
        String publicRouteEmail = "bartholomew.featherstonehaugh@example.test";
        String publicRoutePhone = "+972504445555";
        publicBook(
                bookable.slug(),
                body(
                        "serviceId", bookable.serviceId(),
                        "employeeId", bookable.employeeId(),
                        "startsAt", slots.get(slots.size() - 1).toString(),
                        "customerName", publicRouteName,
                        "customerEmail", publicRouteEmail,
                        "customerPhone", publicRoutePhone));

        Map<String, String> forbidden = new LinkedHashMap<>();
        for (String value : List.of(ownerRouteName, ownerRouteEmail, ownerRoutePhone,
                publicRouteName, publicRouteEmail, publicRoutePhone)) {
            forbidden.put(value, "a customer's own details");
        }
        // The parts, too: a leak that prints only a surname is still a leak.
        forbidden.put("Quattrocchi", "a customer's surname");
        forbidden.put("Featherstonehaugh", "a customer's surname");
        forbidden.put("wilhelmina.quattrocchi", "a customer's email local part");
        forbidden.put("509876543", "a customer's phone digits");
        forbidden.put("504445555", "a customer's phone digits");
        // And the rows that identify them.
        for (Map<String, Object> row : jdbc().queryForList(
                "select id::text as id from customers where business_id = ?::uuid", bookable.businessId())) {
            forbidden.put(String.valueOf(row.get("id")), "a customer id");
        }
        for (Map<String, Object> row : jdbc().queryForList(
                "select id::text as id from appointments where business_id = ?::uuid", bookable.businessId())) {
            forbidden.put(String.valueOf(row.get("id")), "another appointment's id");
        }

        assertNoPublicResponseContains(bookable, forbidden);
    }

    /** 3.14 — no employee for a service they do not perform, and no internal id booking does not need. */
    @Test
    @DisplayName("3.14 the public surface discloses nothing internal")
    void publicSurfaceDisclosesNothingInternal() {
        Bookable bookable = newBookableBusiness("public-internal", 60);

        // A second service, and an employee who performs only that one.
        String otherServiceId = newService(bookable.owner(), bookable.businessId(),
                        "Other Service " + UUID.randomUUID(), 30)
                .path("id")
                .asText();
        String otherEmployeeId = newEmployee(bookable.owner(), bookable.businessId(),
                        "Only Other " + UUID.randomUUID())
                .path("id")
                .asText();
        linkServices(bookable.owner(), bookable.businessId(), otherEmployeeId, otherServiceId);
        for (java.time.DayOfWeek weekday : java.time.DayOfWeek.values()) {
            newWorkingHours(bookable.owner(), bookable.businessId(), otherEmployeeId, weekday,
                    "09:00:00", "17:00:00");
        }

        JsonNode availability = json(publicAvailability(bookable.slug(), bookable.serviceId(), null, BOOKING_DATE));
        List<String> offered = new ArrayList<>();
        availability.path("slots").forEach(slot -> slot.path("employeeIds").forEach(id -> offered.add(id.asText())));
        assertThat(offered)
                .as("an employee who does not perform this service must never be offered for it: a "
                        + "visitor who picks them arrives to somebody who cannot serve them")
                .doesNotContain(otherEmployeeId)
                .contains(bookable.employeeId());

        // Ids the public surface has no business carrying. Booking needs a service and an employee;
        // it does not need the business's primary key, the owner's account, or a blocked time.
        Map<String, String> forbidden = new LinkedHashMap<>();
        forbidden.put(bookable.businessId(), "the business's internal id — the public handle is the slug");
        forbidden.put(bookable.owner().userId(), "the owner's user id");
        String blockedTimeId = newBlockedTime(bookable.owner(), bookable.businessId(), null,
                        Instant.parse("2026-10-20T07:00:00Z"), Instant.parse("2026-10-20T08:00:00Z"), "Private reason")
                .path("id")
                .asText();
        forbidden.put(blockedTimeId, "a blocked time's id");
        forbidden.put("Private reason", "why the business is unavailable, which is nobody else's business");
        for (Map<String, Object> row : jdbc().queryForList(
                "select id::text as id from working_hours where business_id = ?::uuid", bookable.businessId())) {
            forbidden.put(String.valueOf(row.get("id")), "a working-hours row id");
        }

        assertNoPublicResponseContains(bookable, forbidden);
    }

    /** 3.16 — a customer belongs to one business, and the same email in another business is another person. */
    @Test
    @DisplayName("3.16 a public booking creates or reuses a customer of that business only")
    void customerIsPerBusiness() {
        Bookable first = newBookableBusiness("customer-a", 60);
        Bookable second = newBookableBusiness("customer-b", 60);
        String sharedEmail = "same-person-" + UUID.randomUUID() + "@example.test";

        List<Instant> firstSlots = availableStarts(first.owner(), first.businessId(), first.serviceId(),
                first.employeeId(), BOOKING_DATE);
        for (Instant slot : List.of(firstSlots.get(0), firstSlots.get(firstSlots.size() - 1))) {
            assertThat(publicBook(first.slug(), body(
                                    "serviceId", first.serviceId(),
                                    "employeeId", first.employeeId(),
                                    "startsAt", slot.toString(),
                                    "customerName", "Same Person",
                                    "customerEmail", sharedEmail,
                                    "customerPhone", "+972500000001"))
                            .getStatusCode()
                            .value())
                    .as("booking twice with one email")
                    .isEqualTo(201);
        }

        assertThat(publicBook(second.slug(), body(
                                "serviceId", second.serviceId(),
                                "employeeId", second.employeeId(),
                                "startsAt", firstAvailableStart(second).toString(),
                                "customerName", "Same Person",
                                "customerEmail", sharedEmail,
                                "customerPhone", "+972500000001"))
                        .getStatusCode()
                        .value())
                .as("the same person booking a second salon")
                .isEqualTo(201);

        Integer inFirst = jdbc().queryForObject(
                "select count(*) from customers where business_id = ?::uuid and lower(email) = lower(?)",
                Integer.class, first.businessId(), sharedEmail);
        Integer inSecond = jdbc().queryForObject(
                "select count(*) from customers where business_id = ?::uuid and lower(email) = lower(?)",
                Integer.class, second.businessId(), sharedEmail);
        assertThat(inFirst).as("two bookings at one salon are one customer there").isEqualTo(1);
        assertThat(inSecond).as("and the second salon has its own record of that person").isEqualTo(1);

        String customerOfFirst = jdbc().queryForObject(
                "select id::text from customers where business_id = ?::uuid and lower(email) = lower(?)",
                String.class, first.businessId(), sharedEmail);
        Integer crossed = jdbc().queryForObject(
                "select count(*) from appointments where business_id = ?::uuid and customer_id = ?::uuid",
                Integer.class, second.businessId(), customerOfFirst);
        assertThat(crossed)
                .as("no appointment of one salon may point at another salon's customer: one salon "
                        + "has no business knowing the other's clientele")
                .isZero();
    }

    /** 3.17 — an unknown slug and a slug that exists but cannot be booked answer identically. */
    @Test
    @DisplayName("3.17 unknown and unbookable slugs are indistinguishable")
    void unknownAndUnbookableSlugsAreIndistinguishable() {
        Account owner = newAccount("unbookable");
        JsonNode empty = newBusiness(owner, "Nothing Bookable " + UUID.randomUUID(), "Asia/Jerusalem");
        String unbookableSlug = empty.path("slug").asText();
        String unknownSlug = "no-such-business-" + UUID.randomUUID();

        ResponseEntity<String> unknown = publicBusiness(unknownSlug);
        ResponseEntity<String> unbookable = publicBusiness(unbookableSlug);

        assertThat(unbookable.getStatusCode())
                .as("a business with nothing to book must answer exactly as one that does not "
                        + "exist, or the slug space becomes a directory of who has an account")
                .isEqualTo(unknown.getStatusCode());
        assertThat(unbookable.getBody())
                .as("and byte for byte the same body")
                .isEqualTo(unknown.getBody());
        assertThat(String.valueOf(unbookable.getBody()))
                .as("neither answer may name the business it declined to describe")
                .doesNotContain(unbookableSlug)
                .doesNotContain("Nothing Bookable");
    }

    // ------------------------------------------------------------------- the sweep

    /** Every public response this test can reach, labelled for the failure message. */
    private Map<String, ResponseEntity<String>> everyPublicResponse(Bookable bookable) {
        Map<String, ResponseEntity<String>> responses = new LinkedHashMap<>();
        responses.put("GET public business", publicBusiness(bookable.slug()));
        responses.put("GET public availability",
                publicAvailability(bookable.slug(), bookable.serviceId(), null, BOOKING_DATE));
        responses.put("GET public availability, employee named",
                publicAvailability(bookable.slug(), bookable.serviceId(), bookable.employeeId(), BOOKING_DATE));
        responses.put("GET public availability, the day after",
                publicAvailability(bookable.slug(), bookable.serviceId(), null, BOOKING_DATE.plusDays(1)));
        responses.put("GET public availability, a service that is not there",
                publicAvailability(bookable.slug(), UUID.randomUUID().toString(), null, BOOKING_DATE));
        responses.put("GET public business, unknown slug", publicBusiness("nobody-" + UUID.randomUUID()));
        // A booking of the visitor's own, whose confirmation is theirs to see.
        List<Instant> free = availableStarts(bookable.owner(), bookable.businessId(), bookable.serviceId(),
                bookable.employeeId(), BOOKING_DATE);
        if (!free.isEmpty()) {
            responses.put("POST public booking, the visitor's own", publicBook(bookable.slug(), body(
                    "serviceId", bookable.serviceId(),
                    "employeeId", bookable.employeeId(),
                    "startsAt", free.get(free.size() / 2).toString(),
                    "customerName", "Sweep Visitor",
                    "customerEmail", "sweep-" + UUID.randomUUID() + "@example.test",
                    "customerPhone", "+972500000009")));
        }
        responses.put("POST public booking, a time already taken", publicBook(bookable.slug(), body(
                "serviceId", bookable.serviceId(),
                "employeeId", bookable.employeeId(),
                "startsAt", free.isEmpty() ? Instant.parse("2026-10-14T07:00:00Z").toString()
                        : free.get(0).toString(),
                "customerName", "Sweep Loser",
                "customerEmail", "sweep-loser-" + UUID.randomUUID() + "@example.test",
                "customerPhone", "+972500000010")));
        responses.put("POST public booking, malformed", publicBook(bookable.slug(), "{\"serviceId\": "));
        return responses;
    }

    private void assertNoPublicResponseContains(Bookable bookable, Map<String, String> forbidden) {
        Map<String, ResponseEntity<String>> responses = everyPublicResponse(bookable);
        assertThat(responses).as("public responses to sweep").isNotEmpty();

        SoftAssertions soft = new SoftAssertions();
        for (Map.Entry<String, ResponseEntity<String>> response : responses.entrySet()) {
            String body = String.valueOf(response.getValue().getBody());
            String haystack = body.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> secret : forbidden.entrySet()) {
                soft.assertThat(haystack)
                        .as("%s (status %s) must not contain %s — %s",
                                response.getKey(),
                                response.getValue().getStatusCode(),
                                secret.getValue(),
                                secret.getKey())
                        .doesNotContain(secret.getKey().toLowerCase(Locale.ROOT));
            }
        }
        soft.assertAll();
    }
}
