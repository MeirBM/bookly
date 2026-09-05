package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * Turn-2 criteria 2.23 and 2.24: what a refusal looks like, and when it is decided.
 *
 * <p><strong>Not asserted here, deliberately:</strong> whether {@code fieldErrors} must appear on
 * a refusal. {@code CLAUDE.md} names the single error shape as
 * {@code { code, message, fieldErrors }}; the implementation omits the member when there are no
 * field errors, which is an ordinary JSON convention. The criterion says "the standard error body"
 * and does not settle which of those it means, so asserting either reading would be resolving an
 * ambiguity by assertion rather than reporting it. What is asserted instead is that every refusal
 * has the <em>same</em> shape as every other, which both readings require. The question is in the
 * hand-back for the spec to settle.
 *
 * <p>Both criteria come from the same architectural move — the tenant check ran as
 * {@code @PreAuthorize} and now runs in the security filter chain — and both are regressions of
 * defects that moving it exposed. They are asserted separately because they fail separately.
 */
class AccessDeniedContractIT extends ApiIntegrationTest {

    private record Outsider(Account caller, String othersBusinessId) {}

    private Outsider anOutsider(String label) {
        Account stranger = newAccount(label + "-outsider");
        newBusiness(stranger, "Outsider Own " + label, "Asia/Jerusalem");

        Account owner = newAccount(label + "-owner");
        String othersBusinessId =
                newBusiness(owner, "Private " + label, "Asia/Jerusalem").path("id").asText();
        return new Outsider(stranger, othersBusinessId);
    }

    /**
     * 2.23 — a refusal is 403 with the standard error body, and never 401.
     *
     * <p>401 tells an authenticated caller to authenticate, which they have already done and which
     * cannot help them. It arose here from a real mechanism rather than carelessness: Spring's
     * default {@code AccessDeniedHandler} calls {@code sendError}, the container re-dispatches the
     * request as ERROR, every {@code OncePerRequestFilter} correctly declines to run a second time,
     * and the second pass therefore looks anonymous.
     */
    @Test
    @DisplayName("2.23 a refusal is 403 with the standard error body, never 401")
    void refusalIsForbiddenWithTheStandardErrorBody() {
        Outsider outsider = anOutsider("body");
        String base = "/api/businesses/" + outsider.othersBusinessId();

        List<String> routes = List.of(
                base,
                base + "/services",
                base + "/employees",
                base + "/blocked-times");

        SoftAssertions soft = new SoftAssertions();
        List<List<String>> shapes = new ArrayList<>();
        for (String path : routes) {
            ResponseEntity<String> response = get(path, outsider.caller().accessToken());

            soft.assertThat(response.getStatusCode().value())
                    .as("GET %s as a non-member", path)
                    .isEqualTo(403);
            soft.assertThat(response.getStatusCode().value())
                    .as("GET %s must never answer 401: the caller is authenticated, and telling "
                            + "them to authenticate again is advice that cannot work", path)
                    .isNotEqualTo(401);
            soft.assertThat(response.getHeaders().getFirst("WWW-Authenticate"))
                    .as("GET %s must not challenge for credentials it already accepted", path)
                    .isNull();

            JsonNode error = json(response);
            soft.assertThat(error.path("code").asText())
                    .as("%s: the standard error shape carries a stable code a client can branch on", path)
                    .isNotBlank();
            soft.assertThat(error.path("message").asText())
                    .as("%s: the standard error shape carries a message", path)
                    .isNotBlank();
            soft.assertThat(String.valueOf(response.getBody()))
                    .as("%s: a refusal never carries a stack trace or names internals", path)
                    .doesNotContain("\tat ", ".java:", "Exception");

            List<String> keys = new ArrayList<>();
            error.fieldNames().forEachRemaining(keys::add);
            java.util.Collections.sort(keys);
            shapes.add(keys);
        }
        // "The standard error body" is one shape, not one per route: a client that can read the
        // refusal from one tenant-scoped route can read it from all of them.
        soft.assertThat(shapes)
                .as("every refusal must have the same body shape; these were %s", shapes)
                .allMatch(keys -> keys.equals(shapes.get(0)));
        soft.assertAll();
    }

    /**
     * 2.24 — authorization is decided before argument validation.
     *
     * <p>An outsider who gets 400 for a missing parameter has learned what the endpoint accepts,
     * and has learned it from a business they have no relationship with. The control at the end is
     * what stops this test passing for the wrong reason: a member making the same malformed request
     * must still get 400, or the suite would be satisfied by an endpoint that answers 403 to
     * everyone.
     */
    @Test
    @DisplayName("2.24 an outsider gets 403 even when the request is malformed")
    void authorizationPrecedesValidation() {
        Outsider outsider = anOutsider("validation");
        String base = "/api/businesses/" + outsider.othersBusinessId();
        String token = outsider.caller().accessToken();

        SoftAssertions soft = new SoftAssertions();

        // Required query parameters entirely absent.
        soft.assertThat(get(base + "/availability", token).getStatusCode().value())
                .as("availability with no serviceId and no date, as an outsider")
                .isEqualTo(403);

        // Present but unparseable.
        soft.assertThat(get(base + "/availability?serviceId=not-a-uuid&date=not-a-date", token)
                        .getStatusCode()
                        .value())
                .as("availability with a malformed serviceId and date, as an outsider")
                .isEqualTo(403);

        // One of the two missing.
        soft.assertThat(get(base + "/availability?date=" + LocalDate.of(2026, 6, 10), token)
                        .getStatusCode()
                        .value())
                .as("availability with the serviceId missing, as an outsider")
                .isEqualTo(403);

        // A body that would fail validation on its own.
        soft.assertThat(post(base + "/services", body("durationMinutes", -5), token)
                        .getStatusCode()
                        .value())
                .as("creating a service with an invalid body, as an outsider")
                .isEqualTo(403);

        // A malformed path variable behind the business id.
        soft.assertThat(send(HttpMethod.DELETE, base + "/services/not-a-uuid", null, token)
                        .getStatusCode()
                        .value())
                .as("deleting a malformed service id, as an outsider")
                .isEqualTo(403);

        soft.assertAll();

        // The control: the same malformed request from someone entitled to the business must be
        // answered on its merits, or "403 for everything" would pass the assertions above.
        Account owner = newAccount("validation-member");
        String ownBusinessId = newBusiness(owner, "Own Business", "Asia/Jerusalem").path("id").asText();

        assertThat(get("/api/businesses/" + ownBusinessId + "/availability", owner.accessToken())
                        .getStatusCode()
                        .value())
                .as("a member making the same parameterless request is told what is wrong with it, "
                        + "which is how we know the 403s above were about membership")
                .isEqualTo(400);
    }
}
