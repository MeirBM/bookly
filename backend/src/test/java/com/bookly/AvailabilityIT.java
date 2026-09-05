package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * Turn-2 criteria 2.6 and 2.13, over HTTP.
 *
 * <p>Everything the engine can decide on its own is decided in {@code AvailabilityCalculatorTest}.
 * What is left here needs the database: whether the right employees are fed into the engine at all,
 * and whether the zone comes from the business rather than from the server.
 */
class AvailabilityIT extends ApiIntegrationTest {

    /** A Wednesday well clear of any transition; the weekday is read from it, never assumed. */
    private static final LocalDate DATE = LocalDate.of(2026, 6, 10);

    private static final String LOCAL_OPEN = "09:00:00";
    private static final String LOCAL_CLOSE = "12:00:00";

    private static List<JsonNode> slotsOf(ResponseEntity<String> response) {
        List<JsonNode> slots = new ArrayList<>();
        json(response).path("slots").forEach(slots::add);
        return slots;
    }

    private static List<String> employeeIdsIn(JsonNode slot) {
        List<String> ids = new ArrayList<>();
        slot.path("employeeIds").forEach(id -> ids.add(id.asText()));
        return ids;
    }

    /** 2.6 — an employee not linked to the requested service contributes no slots. */
    @Test
    @DisplayName("2.6 an employee not linked to the service contributes nothing")
    void unlinkedEmployeeContributesNothing() {
        Account owner = newAccount("avail-link");
        String businessId = newBusiness(owner, "Linkage Salon", "Asia/Jerusalem").path("id").asText();
        String serviceId = newService(owner, businessId, "Haircut", 60).path("id").asText();

        String linked = newEmployee(owner, businessId, "Linked Employee").path("id").asText();
        String unlinked = newEmployee(owner, businessId, "Unlinked Employee").path("id").asText();
        linkServices(owner, businessId, linked, serviceId);
        // The other employee works exactly the same hours and performs no service at all.
        newWorkingHours(owner, businessId, linked, DATE.getDayOfWeek(), LOCAL_OPEN, LOCAL_CLOSE);
        newWorkingHours(owner, businessId, unlinked, DATE.getDayOfWeek(), LOCAL_OPEN, LOCAL_CLOSE);

        ResponseEntity<String> anyEmployee = availability(owner, businessId, serviceId, null, DATE);

        assertThat(anyEmployee.getStatusCode().value()).as("GET availability").isEqualTo(200);
        List<JsonNode> slots = slotsOf(anyEmployee);
        assertThat(slots).as("the linked employee works three hours, so there are slots").isNotEmpty();
        for (JsonNode slot : slots) {
            assertThat(employeeIdsIn(slot))
                    .as("slot at %s may only name employees who perform this service",
                            slot.path("start").asText())
                    .containsExactly(linked)
                    .doesNotContain(unlinked);
        }

        ResponseEntity<String> askingForTheUnlinkedOne =
                availability(owner, businessId, serviceId, unlinked, DATE);

        assertThat(askingForTheUnlinkedOne.getStatusCode().value())
                .as("asking about an employee who does not perform the service is a real question "
                        + "with an empty answer, not an error")
                .isEqualTo(200);
        assertThat(slotsOf(askingForTheUnlinkedOne))
                .as("an employee who does not perform the service has no availability for it, "
                        + "however free their diary is")
                .isEmpty();
    }

    /**
     * 2.13 — the same request for two businesses in different zones returns different instants for
     * the same local hours. The zone is the business's, not the server's and not the caller's.
     */
    @Test
    @DisplayName("2.13 the zone is taken from the business, not the server")
    void zoneIsTakenFromTheBusiness() {
        Account owner = newAccount("avail-zone");
        ZoneId east = ZoneId.of("Asia/Jerusalem");
        ZoneId west = ZoneId.of("America/New_York");

        String eastId = newBusiness(owner, "Eastern Salon", east.getId()).path("id").asText();
        String westId = newBusiness(owner, "Western Salon", west.getId()).path("id").asText();

        List<Instant> firsts = new ArrayList<>();
        for (String businessId : List.of(eastId, westId)) {
            String serviceId = newService(owner, businessId, "Haircut", 60).path("id").asText();
            String employeeId = newEmployee(owner, businessId, "Same Hours").path("id").asText();
            linkServices(owner, businessId, employeeId, serviceId);
            newWorkingHours(owner, businessId, employeeId, DATE.getDayOfWeek(), LOCAL_OPEN, LOCAL_CLOSE);

            ResponseEntity<String> response = availability(owner, businessId, serviceId, null, DATE);
            assertThat(response.getStatusCode().value()).as("GET availability").isEqualTo(200);
            List<JsonNode> slots = slotsOf(response);
            assertThat(slots).as("identical local hours must produce slots in both businesses").isNotEmpty();
            firsts.add(Instant.parse(slots.get(0).path("start").asText()));
        }

        Instant expectedEast = LocalTime.parse(LOCAL_OPEN).atDate(DATE).atZone(east).toInstant();
        Instant expectedWest = LocalTime.parse(LOCAL_OPEN).atDate(DATE).atZone(west).toInstant();

        assertThat(firsts.get(0))
                .as("09:00 in %s on %s", east, DATE)
                .isEqualTo(expectedEast);
        assertThat(firsts.get(1))
                .as("09:00 in %s on %s — the same wall clock is a different moment", west, DATE)
                .isEqualTo(expectedWest);
        assertThat(firsts.get(0))
                .as("two businesses opening at 09:00 in different zones do not open at the same "
                        + "moment; equal instants would mean the zone came from somewhere else")
                .isNotEqualTo(firsts.get(1));
    }

    /**
     * 2.7's other half, which the pure calculator's signature cannot decide: it takes no employee
     * and returns bare instants, so "each slot names which employees can serve it" is only visible
     * here. Reported as an ambiguity in the spec's assignment of 2.7 to a unit test.
     */
    @Test
    @DisplayName("2.7 with no employee requested, each slot names every employee who can serve it")
    void slotsNameEveryEligibleEmployee() {
        Account owner = newAccount("avail-union");
        String businessId = newBusiness(owner, "Union Salon", "Asia/Jerusalem").path("id").asText();
        String serviceId = newService(owner, businessId, "Haircut", 60).path("id").asText();

        String morning = newEmployee(owner, businessId, "Morning Only").path("id").asText();
        String allDay = newEmployee(owner, businessId, "All Day").path("id").asText();
        linkServices(owner, businessId, morning, serviceId);
        linkServices(owner, businessId, allDay, serviceId);
        newWorkingHours(owner, businessId, morning, DATE.getDayOfWeek(), "09:00:00", "11:00:00");
        newWorkingHours(owner, businessId, allDay, DATE.getDayOfWeek(), "09:00:00", "15:00:00");

        List<JsonNode> slots = slotsOf(availability(owner, businessId, serviceId, null, DATE));

        assertThat(slots).isNotEmpty();
        assertThat(slots.stream().map(s -> s.path("start").asText()).toList())
                .as("the union is deduplicated by start instant, not one entry per employee")
                .doesNotHaveDuplicates();

        Instant nineOClock = LocalTime.parse("09:00:00").atDate(DATE).atZone(ZoneId.of("Asia/Jerusalem")).toInstant();
        Instant noon = LocalTime.parse("12:00:00").atDate(DATE).atZone(ZoneId.of("Asia/Jerusalem")).toInstant();

        JsonNode shared = slots.stream()
                .filter(s -> Instant.parse(s.path("start").asText()).equals(nineOClock))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no slot at 09:00 local, which both work"));
        assertThat(employeeIdsIn(shared))
                .as("an hour both employees are free must name both, or the caller cannot tell "
                        + "there is a choice")
                .containsExactlyInAnyOrder(morning, allDay);

        JsonNode afternoon = slots.stream()
                .filter(s -> Instant.parse(s.path("start").asText()).equals(noon))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no slot at 12:00 local, which one works"));
        assertThat(employeeIdsIn(afternoon))
                .as("an hour only one employee is free must name only that one")
                .containsExactly(allDay);
    }

    /**
     * 2.27 — the response states the step it was computed on, and the slots it returns are the grid
     * that step describes.
     *
     * <p>Stating the number is the easy half and proving it is the half that matters: a response
     * that declared a step its slots did not follow would mislead a client more thoroughly than one
     * that said nothing. So the step is read from the response and every returned start is then
     * checked against it, rather than the test assuming a step of its own.
     */
    @Test
    @DisplayName("2.27 the response states the step, and the slots follow it")
    void responseStatesTheStep() {
        Account owner = newAccount("avail-step");
        ZoneId zone = ZoneId.of("Asia/Jerusalem");
        String businessId = newBusiness(owner, "Grid Salon", zone.getId()).path("id").asText();
        int serviceMinutes = 60;
        String serviceId = newService(owner, businessId, "Haircut", serviceMinutes).path("id").asText();
        String employeeId = newEmployee(owner, businessId, "On The Grid").path("id").asText();
        linkServices(owner, businessId, employeeId, serviceId);
        newWorkingHours(owner, businessId, employeeId, DATE.getDayOfWeek(), LOCAL_OPEN, LOCAL_CLOSE);

        ResponseEntity<String> response = availability(owner, businessId, serviceId, null, DATE);

        assertThat(response.getStatusCode().value()).as("GET availability").isEqualTo(200);
        JsonNode payload = json(response);
        assertThat(payload.has("stepMinutes"))
                .as("the response must state the grid it was computed on; without it a client "
                        + "cannot tell a sparse day from a coarse step")
                .isTrue();
        long stepMinutes = payload.path("stepMinutes").asLong();
        assertThat(stepMinutes).as("stepMinutes").isPositive();

        Duration step = Duration.ofMinutes(stepMinutes);
        Duration serviceDuration = Duration.ofMinutes(serviceMinutes);
        Instant open = LocalTime.parse(LOCAL_OPEN).atDate(DATE).atZone(zone).toInstant();
        Instant close = LocalTime.parse(LOCAL_CLOSE).atDate(DATE).atZone(zone).toInstant();

        List<JsonNode> slots = slotsOf(response);
        assertThat(slots).as("a three-hour window holds at least one one-hour service").isNotEmpty();

        List<Instant> starts = slots.stream().map(s -> Instant.parse(s.path("start").asText())).toList();
        assertThat(starts.get(0))
                .as("the grid begins at the start of the working window")
                .isEqualTo(open);
        for (int i = 1; i < starts.size(); i++) {
            assertThat(Duration.between(starts.get(i - 1), starts.get(i)))
                    .as("consecutive starts in an uninterrupted window are exactly one step apart, "
                            + "and the response says that step is %s", step)
                    .isEqualTo(step);
        }
        for (Instant start : starts) {
            assertThat(Duration.between(open, start).toMinutes() % stepMinutes)
                    .as("%s is on the grid the response declares", start)
                    .isZero();
        }

        long expectedSlots = Duration.between(open, close).minus(serviceDuration).dividedBy(step) + 1;
        assertThat(starts.size())
                .as("every step from %s at which a %d-minute service still finishes by %s",
                        LOCAL_OPEN, serviceMinutes, LOCAL_CLOSE)
                .isEqualTo((int) expectedSlots);

        for (JsonNode slot : slots) {
            assertThat(Duration.between(
                            Instant.parse(slot.path("start").asText()),
                            Instant.parse(slot.path("end").asText())))
                    .as("a slot lasts as long as the service it is offered for")
                    .isEqualTo(serviceDuration);
        }
    }
}
