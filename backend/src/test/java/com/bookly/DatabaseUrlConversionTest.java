package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookly.common.DatabaseUrlEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

/** Contract: a provider-style DATABASE_URL becomes a JDBC url plus credentials; a JDBC url is untouched. */
class DatabaseUrlConversionTest {

    private MockEnvironment run(String databaseUrl) {
        MockEnvironment env = new MockEnvironment().withProperty("DATABASE_URL", databaseUrl);
        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
        return env;
    }

    @Test
    void providerUrlBecomesJdbcWithCredentialsAndTls() {
        MockEnvironment env = run("postgres://app:p%40ss@db.example.com/bookly");  // allow-secret
        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.example.com:5432/bookly?sslmode=require");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("app");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("p@ss");
    }

    @Test
    void explicitSslmodeAndPortAreKept() {
        MockEnvironment env = run("postgresql://u:p@db.example.com:6543/b?sslmode=verify-full");  // allow-secret
        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.example.com:6543/b?sslmode=verify-full");
    }

    @Test
    void localhostDoesNotForceTls() {
        MockEnvironment env = run("postgres://u:p@localhost:5432/b");  // allow-secret
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/b");
    }

    @Test
    void jdbcUrlIsLeftAlone() {
        MockEnvironment env = run("jdbc:postgresql://postgres:5432/bookly");
        assertThat(env.getProperty("spring.datasource.url")).isNull();
    }

    @Test
    void malformedUrlFailsWithoutEchoingIt() {
        assertThatThrownBy(() -> run("postgres://u:secret-pw@/"))  // allow-secret
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("secret-pw");
    }
}
