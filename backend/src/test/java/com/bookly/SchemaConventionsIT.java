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
 * <p>Turn-2 criterion 2.25 makes the rule two-directional: every {@code *_at} column is
 * {@code timestamptz} <em>and</em> every {@code *_local} column is {@code time without time zone}.
 * Enforced one way only, it would let a genuine instant stored as {@code time} pass unnoticed —
 * which is how a booking ends up an hour out twice a year and nobody notices until a customer
 * arrives to a closed shop.
 *
 * <p>Turn-2 criterion 2.9 is here too: availability is computed per request, so no table stores
 * generated slots.
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

    /**
     * 2.25, the second direction — a column named {@code *_local} is a wall-clock time in the
     * business's own zone and must be stored as {@code time}, never as an instant. The name is the
     * contract: {@code start_local} is "09:00 wherever this business is", and storing it as
     * {@code timestamptz} would pin it to one offset and shift it twice a year.
     */
    @Test
    @DisplayName("2.25 every *_local column is a time without time zone")
    void everyLocalColumnIsATimeWithoutTimeZone() {
        List<Map<String, Object>> localColumns = jdbc().queryForList(
                "select table_name, column_name, data_type from information_schema.columns "
                        + "where table_schema = 'public' and table_name <> ? "
                        + "and column_name like '%_local' "
                        + "order by table_name, column_name",
                FLYWAY);

        assertThat(localColumns)
                .as("columns named *_local; none means the convention has nothing to hold and the "
                        + "criterion cannot be decided")
                .isNotEmpty();

        List<String> wrongType = localColumns.stream()
                .filter(c -> !"time without time zone".equals(String.valueOf(c.get("data_type"))))
                .map(c -> c.get("table_name") + "." + c.get("column_name") + " is " + c.get("data_type"))
                .toList();
        assertThat(wrongType)
                .as("columns whose name promises a wall-clock time and whose type delivers "
                        + "something else")
                .isEmpty();
    }

    /**
     * 2.25, stated as the rule rather than as two lists: no column may carry both suffixes, and no
     * column of type {@code time} may be named like an instant. The two tests above would each pass
     * while a column called {@code starts_at_local} satisfied neither reading.
     */
    @Test
    @DisplayName("2.25 no column's name and storage type disagree")
    void noColumnNameContradictsItsStorageType() {
        List<Map<String, Object>> timeColumns = jdbc().queryForList(
                "select table_name, column_name, data_type from information_schema.columns "
                        + "where table_schema = 'public' and table_name <> ? "
                        + "and data_type in ('time without time zone', 'time with time zone')",
                FLYWAY);

        List<String> misnamed = timeColumns.stream()
                .filter(c -> !String.valueOf(c.get("column_name")).endsWith("_local"))
                .map(c -> c.get("table_name") + "." + c.get("column_name") + " is "
                        + c.get("data_type") + " but is not named *_local")
                .toList();
        assertThat(misnamed)
                .as("wall-clock columns that do not say so in their name")
                .isEmpty();
    }

    /**
     * 2.9 — availability is computed from the inputs on every request. Nothing stores generated
     * slots, because a stored slot is a second source of truth that goes stale the moment anything
     * it was derived from changes.
     */
    @Test
    @DisplayName("2.9 no table stores generated slots")
    void noPreGeneratedSlotTable() {
        List<Map<String, Object>> tables = jdbc().queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'public' and table_type = 'BASE TABLE' "
                        + "and (table_name like '%slot%' or table_name like '%availabilit%')");

        assertThat(tables)
                .as("tables whose name suggests generated availability is being stored rather than "
                        + "computed")
                .isEmpty();
    }
}
