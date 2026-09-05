package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import com.bookly.support.ApiIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

/**
 * Turn-1 criterion 1.9: stored passwords are BCrypt hashes; the plaintext appears in no row and in
 * no log line.
 *
 * <p>The database half is checked against every text column of every table, not against the one
 * column the implementer had in mind, because the failure this criterion guards against is a copy
 * of the password somewhere nobody thought to look.
 *
 * <p>The log half is checked over the application's own logging: the root logger at INFO, which is
 * the level a deployment runs at, and every {@code com.bookly} logger turned up to TRACE, so that
 * application code is examined at its most verbose. It is deliberately not checked with the whole
 * of Spring, Hibernate Validator and Tomcat forced to TRACE — at that level the servlet container
 * echoes the raw request body by construction, which no implementation of this criterion could
 * prevent, and a test no implementation can pass is not evidence about this one.
 */
class PasswordStorageIT extends ApiIntegrationTest {

    /** Distinctive enough that a substring match anywhere is certainly this password. */
    private static final String PLAINTEXT = "zorbulax-quintessence-7741";

    private static final String CAPTURE_PROBE = "log-capture-probe-9f2c";

    private static final String BCRYPT = "^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$";

    @Test
    @DisplayName("1.9 the password is stored only as a BCrypt hash and never logged in plaintext")
    void passwordIsNeverStoredOrLoggedInPlaintext() {
        String email = uniqueEmail("secret");

        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Logger application = (Logger) LoggerFactory.getLogger("com.bookly");
        Level previousRootLevel = root.getLevel();
        Level previousApplicationLevel = application.getLevel();
        Appender<ILoggingEvent> console = root.getAppender("STDOUT");
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        if (console != null) {
            // Detached only so that the extra logging does not bury the build output; the capture
            // below still sees every event.
            root.detachAppender(console);
        }
        root.setLevel(Level.INFO);
        application.setLevel(Level.TRACE);
        try {
            ResponseEntity<String> registered = register(email, PLAINTEXT, "Secret User");
            assertThat(registered.getStatusCode().value()).as("register").isEqualTo(201);
            assertThat(login(email, PLAINTEXT).getStatusCode().value()).as("login").isEqualTo(200);
            assertThat(login(email, "wrong-" + PLAINTEXT).getStatusCode().value())
                    .as("failed login")
                    .isEqualTo(401);
            // Proves the capture is actually wired, so that "no leak" cannot mean "no capture".
            LoggerFactory.getLogger("com.bookly.logcapture.probe").trace(CAPTURE_PROBE);
        } finally {
            root.setLevel(previousRootLevel);
            application.setLevel(previousApplicationLevel);
            root.detachAppender(captured);
            if (console != null) {
                root.addAppender(console);
            }
        }

        // ---- the stored form is a BCrypt hash
        String hash = jdbc().queryForObject(
                "select password_hash from users where lower(email) = lower(?)", String.class, email);
        assertThat(hash).as("users.password_hash").isNotNull();
        assertThat(hash).as("users.password_hash must be a BCrypt hash").matches(BCRYPT);
        assertThat(hash).as("users.password_hash must not be the plaintext").isNotEqualTo(PLAINTEXT);

        // ---- the plaintext is in no row of any text column of any table
        List<Map<String, Object>> textColumns = jdbc().queryForList(
                "select table_name, column_name from information_schema.columns "
                        + "where table_schema = 'public' "
                        + "and data_type in ('text','character varying','character')");
        assertThat(textColumns).as("text columns to scan").isNotEmpty();

        List<String> leaks = new ArrayList<>();
        for (Map<String, Object> column : textColumns) {
            String table = String.valueOf(column.get("table_name"));
            String name = String.valueOf(column.get("column_name"));
            Integer hits = jdbc().queryForObject(
                    "select count(*) from \"" + table + "\" where position(? in \"" + name + "\") > 0",
                    Integer.class,
                    PLAINTEXT);
            if (hits != null && hits > 0) {
                leaks.add(table + "." + name + " (" + hits + " row(s))");
            }
        }
        assertThat(leaks).as("columns containing the plaintext password").isEmpty();

        // ---- the plaintext is in no log line
        SoftAssertions soft = new SoftAssertions();
        for (ILoggingEvent event : captured.list) {
            String rendered = event.getFormattedMessage()
                    + " " + String.valueOf(event.getMDCPropertyMap())
                    + " " + (event.getThrowableProxy() == null
                            ? "" : event.getThrowableProxy().getMessage());
            soft.assertThat(rendered)
                    .as("log line from %s must not contain the plaintext password", event.getLoggerName())
                    .doesNotContain(PLAINTEXT);
        }
        soft.assertAll();
        assertThat(captured.list.stream().map(ILoggingEvent::getFormattedMessage))
                .as("the log capture must have been listening; without the probe it proves nothing")
                .contains(CAPTURE_PROBE);
    }
}
