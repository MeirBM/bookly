package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.common.RedisUrlEnvironmentPostProcessor;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.mock.env.MockEnvironment;

/**
 * Contract: with no REDIS_URL, the application's own configuration must leave the Redis url unset
 * (an empty url makes Spring refuse to start, and host/port are what compose supplies). With
 * REDIS_URL set, that value is the Redis url.
 */
class RedisUrlConfigTest {

    @Test
    void shippedConfigurationDoesNotSetAnEmptyRedisUrl() throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        // The test profile also has an application.yml and shadows the shipped one, so read all.
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:application.yml")) {
            new YamlPropertySourceLoader().load("application-" + r.getURL(), r)
                    .forEach(env.getPropertySources()::addLast);
        }
        assertThat(env.getProperty("spring.application.name")).isEqualTo("bookly-backend");
        String url = env.getProperty("spring.data.redis.url");
        assertThat(url == null || !url.isBlank()).as("redis url was '%s'", url).isTrue();
    }

    @Test
    void redisUrlEnvironmentVariableBecomesTheRedisUrl() {
        MockEnvironment env = new MockEnvironment().withProperty("REDIS_URL", "rediss://:pw@cache.example.com:6380");
        new RedisUrlEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getProperty("spring.data.redis.url")).isEqualTo("rediss://:pw@cache.example.com:6380"); // allow-secret
    }

    @Test
    void absentRedisUrlLeavesHostAndPortInCharge() {
        MockEnvironment env = new MockEnvironment();
        new RedisUrlEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getProperty("spring.data.redis.url")).isNull();
    }
}
