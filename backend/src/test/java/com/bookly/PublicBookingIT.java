package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bookly.support.ApiIntegrationTest;
import com.bookly.support.Routes;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
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

    // -------------------------------------------------------------------- 3.27

    /**
     * 3.27 — an anonymous booking may fill in a contact detail the customer does not have; it may
     * never change one they do.
     *
     * <p>The original defect let any anonymous request rewrite a person's name and phone against
     * every appointment they had ever made, from a form that asks for nothing but an email address.
     * Anyone who guessed a customer's email could have replaced their phone number with their own,
     * and the salon would have rung a stranger to confirm.
     *
     * <p>Written as an attack: the same email in a different case, an emptied name, and a rival
     * phone number are all tried, and the stored row is read back after each.
     */
    @Test
    @DisplayName("3.27 an anonymous booking cannot rewrite an existing customer's details")
    void anonymousBookingCannotRewriteAnExistingCustomer() {
        Bookable bookable = newBookableBusiness("rewrite", 30);
        List<Instant> slots = availableStarts(bookable.owner(), bookable.businessId(), bookable.serviceId(),
                bookable.employeeId(), BOOKING_DATE);
        assertThat(slots.size()).as("enough room for several bookings").isGreaterThan(8);

        String email = "victim-" + UUID.randomUUID() + "@example.test";
        String realName = "Wilhelmina Quattrocchi";
        String realPhone = "+972501111111";

        assertThat(publicBook(bookable.slug(), body(
                                "serviceId", bookable.serviceId(),
                                "employeeId", bookable.employeeId(),
                                "startsAt", slots.get(0).toString(),
                                "customerName", realName,
                                "customerEmail", email,
                                "customerPhone", realPhone))
                        .getStatusCode()
                        .value())
                .as("the customer's own first booking")
                .isEqualTo(201);

        record Attack(String what, String name, String email, String phone) {}
        List<Attack> attacks = List.of(
                new Attack("a rival name and phone on the same email",
                        "Impostor Iago", email, "+972509999999"),
                new Attack("the same email in a different case, in case the lookup folds but the "
                        + "write does not",
                        "Impostor Uppercase", email.toUpperCase(Locale.ROOT), "+972508888888"),
                new Attack("a blank name, to see whether a customer can be erased rather than "
                        + "replaced",
                        "", email, "+972507777777"));

        int slot = 2;
        for (Attack attack : attacks) {
            Map<String, Object> payload = body(
                    "serviceId", bookable.serviceId(),
                    "employeeId", bookable.employeeId(),
                    "startsAt", slots.get(slot).toString(),
                    "customerEmail", attack.email(),
                    "customerPhone", attack.phone());
            payload.put("customerName", attack.name());
            publicBook(bookable.slug(), payload);
            slot += 2;

            Map<String, Object> stored = jdbc().queryForMap(
                    "select full_name, phone from customers "
                            + "where business_id = ?::uuid and lower(email) = lower(?)",
                    bookable.businessId(),
                    email);
            assertThat(String.valueOf(stored.get("full_name")))
                    .as("%s: the name a customer gave must survive it", attack.what())
                    .isEqualTo(realName);
            assertThat(String.valueOf(stored.get("phone")))
                    .as("%s: so must the phone number the salon would ring", attack.what())
                    .isEqualTo(realPhone);
        }

        Integer rows = jdbc().queryForObject(
                "select count(*) from customers where business_id = ?::uuid and lower(email) = lower(?)",
                Integer.class, bookable.businessId(), email);
        assertThat(rows).as("one email is one customer of this business, whatever case it arrived in")
                .isEqualTo(1);

        // The owner's own list is where the rewrite would have done its damage.
        ResponseEntity<String> owned = get(
                businessPath(bookable.businessId(), "/appointments?from=" + BOOKING_DATE + "&to=" + BOOKING_DATE),
                bookable.owner().accessToken());
        assertThat(owned.getStatusCode().value()).isEqualTo(200);
        json(owned).forEach(appointment -> {
            assertThat(appointment.path("customerName").asText())
                    .as("every appointment of this customer must still name them, including the "
                            + "first one they made before anybody tried to overwrite it")
                    .isEqualTo(realName);
            assertThat(appointment.path("customerPhone").asText()).isEqualTo(realPhone);
        });
        assertThat(String.valueOf(owned.getBody()))
                .as("and no attacker's details reached the owner's screen")
                .doesNotContain("Impostor", "+972509999999", "+972508888888", "+972507777777");

        // The half that is allowed: a detail the customer does not have may be filled in.
        String sparse = "sparse-" + UUID.randomUUID() + "@example.test";
        assertThat(publicBook(bookable.slug(), body(
                                "serviceId", bookable.serviceId(),
                                "employeeId", bookable.employeeId(),
                                "startsAt", slots.get(slot).toString(),
                                "customerName", "Sparse Sam",
                                "customerEmail", sparse))
                        .getStatusCode()
                        .value())
                .as("a booking with no phone number")
                .isEqualTo(201);
        String before = jdbc().queryForObject(
                "select phone from customers where business_id = ?::uuid and lower(email) = lower(?)",
                String.class, bookable.businessId(), sparse);
        assertThat(before).as("nothing was stored for a phone that was never given").isNull();

        assertThat(publicBook(bookable.slug(), body(
                                "serviceId", bookable.serviceId(),
                                "employeeId", bookable.employeeId(),
                                "startsAt", slots.get(slot + 2).toString(),
                                "customerName", "Sparse Sam",
                                "customerEmail", sparse,
                                "customerPhone", "+972506666666"))
                        .getStatusCode()
                        .value())
                .as("the same customer supplying a phone number the second time")
                .isEqualTo(201);
        assertThat(jdbc().queryForObject(
                        "select phone from customers where business_id = ?::uuid and lower(email) = lower(?)",
                        String.class, bookable.businessId(), sparse))
                .as("filling in a blank is not rewriting: the customer gains a phone number they "
                        + "did not have, which is the point of asking for it again")
                .isEqualTo("+972506666666");
    }

    // -------------------------------------------------------------------- 3.29

    /** 3.29 — the roster names only people a visitor could actually book. */
    @Test
    @DisplayName("3.29 the public surface names only bookable people")
    void publicSurfaceNamesOnlyBookablePeople() {
        Bookable bookable = newBookableBusiness("roster", 30);

        // Performs the service, but works no hours at all: nobody can book them.
        String noHours = newEmployee(bookable.owner(), bookable.businessId(), "Rostered Never " + UUID.randomUUID())
                .path("id")
                .asText();
        linkServices(bookable.owner(), bookable.businessId(), noHours, bookable.serviceId());

        // Works every hour of the week, but performs nothing.
        String performsNothing = newEmployee(bookable.owner(), bookable.businessId(),
                        "Qualified For Nothing " + UUID.randomUUID())
                .path("id")
                .asText();
        for (DayOfWeek weekday : DayOfWeek.values()) {
            newWorkingHours(bookable.owner(), bookable.businessId(), performsNothing, weekday,
                    "09:00:00", "17:00:00");
        }

        JsonNode business = json(publicBusiness(bookable.slug()));
        List<String> named = new ArrayList<>();
        business.path("employees").forEach(employee -> named.add(employee.path("id").asText()));

        assertThat(named)
                .as("the person who performs the service and works hours is bookable, so they are named")
                .contains(bookable.employeeId());
        assertThat(named)
                .as("somebody who performs nothing cannot be booked for anything, and naming them "
                        + "offers a visitor a choice that leads nowhere")
                .doesNotContain(performsNothing);
        assertThat(named)
                .as("nor can somebody who works no hours: 3.29 says bookable means performing a "
                        + "service *and* having hours")
                .doesNotContain(noHours);

        // Whoever is named must carry only services they really perform.
        business.path("employees").forEach(employee -> {
            List<String> performs = new ArrayList<>();
            employee.path("serviceIds").forEach(id -> performs.add(id.asText()));
            assertThat(performs)
                    .as("%s is offered for services they do not perform", employee.path("name").asText())
                    .containsExactly(bookable.serviceId());
        });
    }

    /**
     * 3.29 — a business nobody can serve at is refused exactly as an address that never existed.
     *
     * <p>This is the half {@code unknownAndUnbookableSlugsAreIndistinguishable} cannot reach: that
     * test creates a business with nothing in it, and the interesting cases are the ones that look
     * furnished — a service and a person, but the two never linked, or linked and never rostered.
     */
    @Test
    @DisplayName("3.29 a business with nobody able to serve is not discoverable")
    void aBusinessWithNobodyAbleToServeIsNotDiscoverable() {
        Account owner = newAccount("undiscoverable");
        ResponseEntity<String> unknown = publicBusiness("no-such-slug-" + UUID.randomUUID());

        record Shape(String what, boolean link, boolean hours) {}
        List<Shape> shapes = List.of(
                new Shape("a service and a person who does not perform it", false, true),
                new Shape("a person who performs it but works no hours", true, false),
                new Shape("a person who neither performs it nor works", false, false));

        SoftAssertions soft = new SoftAssertions();
        for (Shape shape : shapes) {
            JsonNode business = newBusiness(owner, "Half Set Up " + UUID.randomUUID(), "Asia/Jerusalem");
            String businessId = business.path("id").asText();
            String serviceId = newService(owner, businessId, "Service " + UUID.randomUUID(), 30)
                    .path("id")
                    .asText();
            String employeeId = newEmployee(owner, businessId, "Employee " + UUID.randomUUID())
                    .path("id")
                    .asText();
            if (shape.link()) {
                linkServices(owner, businessId, employeeId, serviceId);
            }
            if (shape.hours()) {
                for (DayOfWeek weekday : DayOfWeek.values()) {
                    newWorkingHours(owner, businessId, employeeId, weekday, "09:00:00", "17:00:00");
                }
            }

            ResponseEntity<String> response = publicBusiness(business.path("slug").asText());

            soft.assertThat(response.getStatusCode())
                    .as("%s: nobody can be booked here, so it must answer as an address that does "
                            + "not exist", shape.what())
                    .isEqualTo(unknown.getStatusCode());
            soft.assertThat(response.getBody())
                    .as("%s: byte for byte the same answer, or the difference is a directory of "
                            + "who has an account", shape.what())
                    .isEqualTo(unknown.getBody());
        }
        soft.assertAll();
    }

    // -------------------------------------------------------------------- 3.31

    /**
     * 3.31 — the difference between {@code SLOT_TAKEN} and {@code SLOT_NOT_AVAILABLE} must reveal
     * nothing about times the availability surface would not have offered anyway.
     *
     * <p>Otherwise the booking endpoint is an oracle for a person's diary: an attacker who cannot
     * see any availability at all can still ask "is this employee busy at 03:00 on Sunday?" and
     * read the answer off the error code. The three probes below are all instants the public
     * surface never offers — a service the employee does not perform, an hour outside their
     * working window, and a day they do not work — and in each case the busy instant and the free
     * instant must be answered identically.
     */
    @Test
    @DisplayName("3.31 the two conflict codes reveal no occupancy")
    void theTwoConflictCodesRevealNoOccupancy() {
        Bookable bookable = newBookableBusiness("oracle", 60);
        ZoneId zone = ZoneId.of(bookable.timezone());

        // Genuinely busy at one instant the surface does offer, and free at another.
        Instant busyOffered = firstAvailableStart(bookable);
        bookOrFail(bookable, busyOffered, "occupant-" + UUID.randomUUID() + "@example.test");
        Instant freeOffered = availableStarts(bookable.owner(), bookable.businessId(), bookable.serviceId(),
                        bookable.employeeId(), BOOKING_DATE)
                .stream()
                .filter(start -> start.isAfter(busyOffered.plusSeconds(3600)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no free slot left to compare against"));

        // Busy and free outside the working window, where nothing is ever offered.
        String customerId = jdbc().queryForObject(
                "insert into customers (business_id, full_name, email) values (?::uuid, ?, ?) returning id::text",
                String.class, bookable.businessId(), "Night Owl", "night-" + UUID.randomUUID() + "@x.test");
        Instant busyAtNight = LocalTime.of(3, 0).atDate(BOOKING_DATE).atZone(zone).toInstant();
        jdbc().update(
                "insert into appointments "
                        + "(business_id, employee_id, service_id, customer_id, starts_at, ends_at, status) "
                        + "values (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?, 'CONFIRMED')",
                bookable.businessId(), bookable.employeeId(), bookable.serviceId(), customerId,
                java.sql.Timestamp.from(busyAtNight),
                java.sql.Timestamp.from(busyAtNight.plusSeconds(3600)));
        Instant freeAtNight = LocalTime.of(5, 0).atDate(BOOKING_DATE).atZone(zone).toInstant();

        // A service this employee does not perform: nothing is offered for it at any hour.
        String unperformedService = newService(bookable.owner(), bookable.businessId(),
                        "Unperformed " + UUID.randomUUID(), 60)
                .path("id")
                .asText();

        assertThat(publicAvailability(bookable.slug(), unperformedService, bookable.employeeId(), BOOKING_DATE)
                        .getStatusCode()
                        .value())
                .as("the probe service is answerable")
                .isEqualTo(200);

        record Probe(String what, String serviceId, Instant busy, Instant free) {}
        List<Probe> probes = List.of(
                new Probe("a service the employee does not perform",
                        unperformedService, busyOffered, freeOffered),
                new Probe("an hour outside the working window",
                        bookable.serviceId(), busyAtNight, freeAtNight),
                new Probe("a service the employee does not perform, probed outside hours",
                        unperformedService, busyAtNight, freeAtNight));

        SoftAssertions soft = new SoftAssertions();
        for (Probe probe : probes) {
            ResponseEntity<String> whenBusy = probeBooking(bookable, probe.serviceId(), probe.busy());
            ResponseEntity<String> whenFree = probeBooking(bookable, probe.serviceId(), probe.free());

            soft.assertThat(whenBusy.getStatusCode())
                    .as("%s: the status must not depend on whether the employee happens to be "
                            + "busy at a time nobody was ever offered", probe.what())
                    .isEqualTo(whenFree.getStatusCode());
            soft.assertThat(json(whenBusy).path("code").asText())
                    .as("%s: neither must the code. A caller who can tell these two apart can read "
                            + "an employee's private diary one instant at a time", probe.what())
                    .isEqualTo(json(whenFree).path("code").asText());
            soft.assertThat(whenBusy.getBody())
                    .as("%s: nor may the message differ", probe.what())
                    .isEqualTo(whenFree.getBody());
            soft.assertThat(whenBusy.getStatusCode().is2xxSuccessful())
                    .as("%s: and neither probe may succeed — these are times the surface does not "
                            + "offer", probe.what())
                    .isFalse();
        }
        soft.assertAll();
    }

    private ResponseEntity<String> probeBooking(Bookable bookable, String serviceId, Instant startsAt) {
        return publicBook(bookable.slug(), body(
                "serviceId", serviceId,
                "employeeId", bookable.employeeId(),
                "startsAt", startsAt.toString(),
                "customerName", "Probing Visitor",
                "customerEmail", "probe-" + UUID.randomUUID() + "@example.test",
                "customerPhone", "+972500000000"));
    }
}
