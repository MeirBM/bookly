package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Turn-1 criterion 1.15: every timestamp column added in this turn is {@code timestamptz}.
 *
 * <p>Read from {@code information_schema} rather than from the migration file, so that what is
 * asserted is the schema the application is actually running against.
 */
class SchemaConventionsIT extends ApiIntegrationTest {

    /** Flyway's own bookkeeping table is not part of this turn's schema. */
    private static final String FLYWAY = "flyway_schema_history";

    @Test
    @DisplayName("1.15 every timestamp column in the turn-1 schema is timestamptz")
    void allTimestampsAreTimestamptz() {
        List<Map<String, Object>> timestampColumns = jdbc().queryForList(
                "select table_name, column_name, data_type from information_schema.columns "
                        + "where table_schema = 'public' and table_name <> ? "
                        + "and data_type like 'timestamp%' "
                        + "order by table_name, column_name",
                FLYWAY);

        assertThat(timestampColumns)
                .as("timestamp columns found in the public schema; none means the criterion "
                        + "cannot be decided")
                .isNotEmpty();

        List<String> notTimestamptz = timestampColumns.stream()
                .filter(c -> !"timestamp with time zone".equals(String.valueOf(c.get("data_type"))))
                .map(c -> c.get("table_name") + "." + c.get("column_name") + " is " + c.get("data_type"))
                .toList();

        assertThat(notTimestamptz)
                .as("columns storing a timestamp without a time zone")
                .isEmpty();
    }

    /**
     * The same rule stated the other way round: every column named like an instant must exist and
     * be a timestamp, so that a column stored as text or as an epoch integer cannot pass the check
     * above by never appearing in it.
     */
    @Test
    @DisplayName("1.15 the audit columns of every turn-1 table are timestamps")
    void auditColumnsAreTimestamps() {
        List<Map<String, Object>> atColumns = jdbc().queryForList(
                "select table_name, column_name, data_type from information_schema.columns "
                        + "where table_schema = 'public' and table_name <> ? "
                        + "and (column_name like '%_at' or column_name like '%_time') "
                        + "order by table_name, column_name",
                FLYWAY);

        assertThat(atColumns).as("columns named like an instant").isNotEmpty();
        List<String> wrongType = atColumns.stream()
                .filter(c -> !"timestamp with time zone".equals(String.valueOf(c.get("data_type"))))
                .map(c -> c.get("table_name") + "." + c.get("column_name") + " is " + c.get("data_type"))
                .toList();
        assertThat(wrongType).as("instant-named columns not stored as timestamptz").isEmpty();
    }
}
