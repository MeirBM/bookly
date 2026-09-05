package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.bookly.support.ApiIntegrationTest;
import com.bookly.support.LogCapture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * Turn-1 criterion 1.23: a malformed request returns 4xx, not 500, and writes no stack trace.
 *
 * <p>The distinction is not cosmetic. A 500 says the server broke; a 4xx says the caller sent
 * something wrong. Converting one into the other hides real faults among routine bad input, makes
 * every monitoring signal built on the 5xx rate useless, and — because the conversion is usually a
 * catch-all that logs the throwable — writes a stack trace naming internal classes into a log an
 * operator may forward somewhere less private than they think.
 */
class MalformedRequestIT extends ApiIntegrationTest {

    private static void assertClientError(ResponseEntity<String> response, String what) {
        assertThat(response.getStatusCode().value())
                .as("%s must be answered as the caller's mistake, not the server's", what)
                .isBetween(400, 499);
        assertThat(String.valueOf(response.getBody()))
                .as("%s: the response must not carry a stack trace", what)
                .doesNotContain("\tat ", ".java:", "Exception", "com.bookly.");
    }

    /** 1.23 — a path variable that is not a UUID. */
    @Test
    @DisplayName("1.23 a non-UUID path variable is a client error")
    void nonUuidPathVariableIsAClientError() {
        Account caller = newAccount("malformed-path");

        ResponseEntity<String> response = get("/api/businesses/not-a-uuid", caller.accessToken());

        assertClientError(response, "GET /api/businesses/not-a-uuid");
    }

    /** 1.23 — a body that is not parseable JSON. */
    @Test
    @DisplayName("1.23 an unparseable body is a client error")
    void unparseableBodyIsAClientError() {
        ResponseEntity<String> response = post("/api/auth/login", "{\"email\": \"a@b.test\", ");

        assertClientError(response, "POST /api/auth/login with a truncated JSON body");
    }

    /** 1.23 — a method the path does not answer. */
    @Test
    @DisplayName("1.23 an unsupported method on an existing path is a client error")
    void unsupportedMethodIsAClientError() {
        ResponseEntity<String> response = send(HttpMethod.DELETE, "/api/auth/login", null, null);

        assertClientError(response, "DELETE /api/auth/login");
    }

    /** 1.23 — a path that does not exist. */
    @Test
    @DisplayName("1.23 an unknown path is a client error")
    void unknownPathIsAClientError() {
        ResponseEntity<String> response = get("/api/there-is-no-such-thing", null);

        assertClientError(response, "GET /api/there-is-no-such-thing");
    }

    /**
     * 1.23, the other half: none of these writes a stack trace to the log either. A malformed
     * request is routine, and treating routine input as an incident is how a log stops being read.
     */
    @Test
    @DisplayName("1.23 no malformed request writes a stack trace to the log")
    void malformedRequestsWriteNoStackTrace() throws Exception {
        Account caller = newAccount("malformed-log");

        List<ILoggingEvent> events = LogCapture.around(() -> {
            get("/api/businesses/not-a-uuid", caller.accessToken());
            post("/api/auth/login", "{\"email\": \"a@b.test\", ");
            send(HttpMethod.DELETE, "/api/auth/login", null, null);
            get("/api/there-is-no-such-thing", null);
        });

        assertThat(events)
                .as("the capture must have been listening")
                .anyMatch(e -> LogCapture.PROBE.equals(e.getFormattedMessage()));

        List<String> withThrowables = events.stream()
                .filter(e -> e.getLevel().isGreaterOrEqual(Level.INFO))
                .filter(e -> e.getThrowableProxy() != null)
                .map(e -> e.getLoggerName() + ": " + e.getFormattedMessage()
                        + " <- " + e.getThrowableProxy().getClassName())
                .toList();

        assertThat(withThrowables)
                .as("log events at INFO or above carrying a throwable while only malformed "
                        + "requests were made; routine bad input is not an incident")
                .isEmpty();
    }
}
