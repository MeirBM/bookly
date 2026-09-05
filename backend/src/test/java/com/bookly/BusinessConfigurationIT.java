package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * Turn-2 criteria 2.14, 2.15, 2.16 and 2.19: the configuration surface a business describes itself
 * through.
 *
 * <p>Creation is asserted by reading the resource back rather than by trusting the response to the
 * write. A create that answers 200 and stores nothing passes the first check and fails the second,
 * and it is the second that the owner experiences.
 */
class BusinessConfigurationIT extends ApiIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 10);

    private record Fixture(Account owner, String businessId) {}

    private Fixture aBusiness(String label) {
        Account owner = newAccount(label);
        return new Fixture(owner, newBusiness(owner, "Config " + label, "Asia/Jerusalem").path("id").asText());
    }

    private List<JsonNode> list(Fixture f, String suffix) {
        ResponseEntity<String> response = get(businessPath(f.businessId(), suffix), f.owner().accessToken());
        assertThat(response.getStatusCode().value()).as("GET %s", suffix).isEqualTo(200);
        List<JsonNode> items = new ArrayList<>();
        json(response).forEach(items::add);
        return items;
    }

    private List<String> idsIn(List<JsonNode> items) {
        return items.stream().map(i -> i.path("id").asText()).toList();
    }

    private ResponseEntity<String> delete(Fixture f, String suffix) {
        return send(HttpMethod.DELETE, businessPath(f.businessId(), suffix), null, f.owner().accessToken());
    }

    // -------------------------------------------------------------------- 2.14

    @Test
    @DisplayName("2.14 services can be created, listed and deleted")
    void servicesCanBeCreatedListedAndDeleted() {
        Fixture f = aBusiness("services");

        JsonNode created = newService(f.owner(), f.businessId(), "Beard Trim", 30);
        String id = created.path("id").asText();
        assertThat(id).as("ServiceResponse.id").isNotBlank();
        assertThat(created.path("name").asText()).isEqualTo("Beard Trim");
        assertThat(created.path("durationMinutes").asInt()).isEqualTo(30);

        assertThat(idsIn(list(f, "/services"))).as("after create").contains(id);

        assertThat(delete(f, "/services/" + id).getStatusCode().is2xxSuccessful())
                .as("DELETE /services/{id}")
                .isTrue();
        assertThat(idsIn(list(f, "/services"))).as("after delete").doesNotContain(id);
    }

    @Test
    @DisplayName("2.14 employees can be created, listed and deleted")
    void employeesCanBeCreatedListedAndDeleted() {
        Fixture f = aBusiness("employees");

        String id = newEmployee(f.owner(), f.businessId(), "Dana Barber").path("id").asText();
        assertThat(idsIn(list(f, "/employees"))).as("after create").contains(id);

        assertThat(delete(f, "/employees/" + id).getStatusCode().is2xxSuccessful())
                .as("DELETE /employees/{id}")
                .isTrue();
        assertThat(idsIn(list(f, "/employees"))).as("after delete").doesNotContain(id);
    }

    @Test
    @DisplayName("2.14 employee-to-service links can be set and read back")
    void employeeServiceLinksCanBeSetAndRead() {
        Fixture f = aBusiness("links");
        String cut = newService(f.owner(), f.businessId(), "Cut", 30).path("id").asText();
        String colour = newService(f.owner(), f.businessId(), "Colour", 90).path("id").asText();
        String employeeId = newEmployee(f.owner(), f.businessId(), "Sam Stylist").path("id").asText();

        linkServices(f.owner(), f.businessId(), employeeId, cut, colour);

        JsonNode employee = list(f, "/employees").stream()
                .filter(e -> e.path("id").asText().equals(employeeId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the employee vanished from the listing"));
        List<String> linked = new ArrayList<>();
        employee.path("serviceIds").forEach(s -> linked.add(s.asText()));
        assertThat(linked).as("both links after PUT").containsExactlyInAnyOrder(cut, colour);

        // The link set is replaced, not added to: dropping a service must actually drop it.
        linkServices(f.owner(), f.businessId(), employeeId, colour);
        JsonNode after = list(f, "/employees").stream()
                .filter(e -> e.path("id").asText().equals(employeeId))
                .findFirst()
                .orElseThrow();
        List<String> remaining = new ArrayList<>();
        after.path("serviceIds").forEach(s -> remaining.add(s.asText()));
        assertThat(remaining).as("PUT replaces the set").containsExactly(colour);
    }

    @Test
    @DisplayName("2.14 working hours can be created, listed and deleted")
    void workingHoursCanBeCreatedListedAndDeleted() {
        Fixture f = aBusiness("hours");
        String employeeId = newEmployee(f.owner(), f.businessId(), "Rota Owner").path("id").asText();

        String id = newWorkingHours(
                        f.owner(), f.businessId(), employeeId, DATE.getDayOfWeek(), "09:00:00", "17:00:00")
                .path("id")
                .asText();

        List<JsonNode> hours = list(f, "/employees/" + employeeId + "/working-hours");
        assertThat(idsIn(hours)).as("after create").contains(id);
        JsonNode window = hours.stream().filter(h -> h.path("id").asText().equals(id)).findFirst().orElseThrow();
        assertThat(window.path("weekday").asText()).isEqualTo(DATE.getDayOfWeek().name());
        assertThat(window.path("startsAt").asText()).as("the local start comes back as stored").startsWith("09:00");
        assertThat(window.path("endsAt").asText()).startsWith("17:00");

        assertThat(delete(f, "/working-hours/" + id).getStatusCode().is2xxSuccessful())
                .as("DELETE /working-hours/{id}")
                .isTrue();
        assertThat(idsIn(list(f, "/employees/" + employeeId + "/working-hours")))
                .as("after delete")
                .doesNotContain(id);
    }

    @Test
    @DisplayName("2.14 blocked times can be created, listed and deleted")
    void blockedTimesCanBeCreatedListedAndDeleted() {
        Fixture f = aBusiness("blocked");
        String employeeId = newEmployee(f.owner(), f.businessId(), "Away Soon").path("id").asText();
        Instant start = DATE.atTime(10, 0).atZone(java.time.ZoneId.of("Asia/Jerusalem")).toInstant();
        Instant end = start.plusSeconds(3600);

        String forOne = newBlockedTime(f.owner(), f.businessId(), employeeId, start, end, "Dentist")
                .path("id")
                .asText();
        // No employee means the whole business: spec part 3, that is how a public holiday is said.
        String forEveryone = newBlockedTime(
                        f.owner(), f.businessId(), null, start.plusSeconds(86400), end.plusSeconds(86400), "Holiday")
                .path("id")
                .asText();

        List<JsonNode> blocked = list(f, "/blocked-times");
        assertThat(idsIn(blocked)).as("after create").contains(forOne, forEveryone);
        JsonNode holiday = blocked.stream().filter(b -> b.path("id").asText().equals(forEveryone)).findFirst().orElseThrow();
        assertThat(holiday.path("employeeId").isNull() || holiday.path("employeeId").asText().isEmpty())
                .as("a business-wide block names no employee")
                .isTrue();

        assertThat(delete(f, "/blocked-times/" + forOne).getStatusCode().is2xxSuccessful())
                .as("DELETE /blocked-times/{id}")
                .isTrue();
        assertThat(idsIn(list(f, "/blocked-times"))).as("after delete").doesNotContain(forOne).contains(forEveryone);
    }

    // -------------------------------------------------------------- 2.15, 2.16

    /** 2.15 — a duration must be positive and a whole number of minutes. */
    @Test
    @DisplayName("2.15 a zero or negative service duration is refused with 400")
    void refusesNonPositiveDuration() {
        Fixture f = aBusiness("duration");

        for (int duration : new int[] {0, -1, -30}) {
            ResponseEntity<String> response = post(
                    businessPath(f.businessId(), "/services"),
                    body("name", "Impossible " + duration, "durationMinutes", duration, "priceMinor", 100L),
                    f.owner().accessToken());

            assertThat(response.getStatusCode().value())
                    .as("a service of %d minutes cannot be performed, let alone scheduled", duration)
                    .isEqualTo(400);
        }
        assertThat(list(f, "/services")).as("nothing was stored").isEmpty();
    }

    /** 2.16 — a working window whose end is not after its start is refused with 400. */
    @Test
    @DisplayName("2.16 an inverted or empty working window is refused with 400")
    void refusesInvertedWorkingWindow() {
        Fixture f = aBusiness("window");
        String employeeId = newEmployee(f.owner(), f.businessId(), "Impossible Rota").path("id").asText();
        String suffix = "/employees/" + employeeId + "/working-hours";

        ResponseEntity<String> inverted = post(
                businessPath(f.businessId(), suffix),
                body("weekday", "MONDAY", "startsAt", "17:00:00", "endsAt", "09:00:00"),
                f.owner().accessToken());
        assertThat(inverted.getStatusCode().value())
                .as("a window that ends before it starts is not a window")
                .isEqualTo(400);

        ResponseEntity<String> empty = post(
                businessPath(f.businessId(), suffix),
                body("weekday", "MONDAY", "startsAt", "09:00:00", "endsAt", "09:00:00"),
                f.owner().accessToken());
        assertThat(empty.getStatusCode().value())
                .as("the criterion says the end must be *after* the start, so equal is refused too")
                .isEqualTo(400);

        assertThat(list(f, suffix)).as("nothing was stored").isEmpty();
    }

    // -------------------------------------------------------------------- 2.19

    /** 2.19 — deleting a service removes its employee links and orphans nothing. */
    @Test
    @DisplayName("2.19 deleting a service removes its employee links")
    void deletingAServiceRemovesItsLinks() {
        Fixture f = aBusiness("cascade");
        String doomed = newService(f.owner(), f.businessId(), "Doomed Service", 30).path("id").asText();
        String kept = newService(f.owner(), f.businessId(), "Kept Service", 45).path("id").asText();
        String employeeId = newEmployee(f.owner(), f.businessId(), "Linked To Both").path("id").asText();
        linkServices(f.owner(), f.businessId(), employeeId, doomed, kept);

        Integer linksBefore = jdbc().queryForObject(
                "select count(*) from employee_services where employee_id = ?::uuid", Integer.class, employeeId);
        assertThat(linksBefore).as("links before the delete").isEqualTo(2);

        assertThat(delete(f, "/services/" + doomed).getStatusCode().is2xxSuccessful())
                .as("DELETE /services/{id}")
                .isTrue();

        JsonNode employee = list(f, "/employees").stream()
                .filter(e -> e.path("id").asText().equals(employeeId))
                .findFirst()
                .orElseThrow();
        List<String> linked = new ArrayList<>();
        employee.path("serviceIds").forEach(s -> linked.add(s.asText()));
        assertThat(linked)
                .as("the link to the deleted service is gone and the other is untouched")
                .containsExactly(kept);

        Integer orphans = jdbc().queryForObject(
                "select count(*) from employee_services es "
                        + "where not exists (select 1 from services s where s.id = es.service_id)",
                Integer.class);
        assertThat(orphans)
                .as("employee_services rows pointing at a service that no longer exists")
                .isZero();

        Integer linkRowsToDoomed = jdbc().queryForObject(
                "select count(*) from employee_services where service_id = ?::uuid", Integer.class, doomed);
        assertThat(linkRowsToDoomed).as("link rows left behind by the delete").isZero();
    }
}
