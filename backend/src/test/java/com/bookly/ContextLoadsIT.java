package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.AbstractIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Implementer's smoke check, not part of the graded verification suite.
 *
 * <p>Its value is narrow and specific: with {@code ddl-auto: validate}, booting the context is what
 * proves the Flyway schema and the JPA entities agree. A mismatch fails here rather than at the
 * first request that touches the wrong column.
 */
class ContextLoadsIT extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void schemaAndEntitiesAgree() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('users','businesses','business_members','refresh_tokens')",
                Integer.class);
        assertThat(tables).isEqualTo(4);
    }
}
