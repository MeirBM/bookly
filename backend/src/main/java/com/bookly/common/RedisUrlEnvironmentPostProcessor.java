package com.bookly.common;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Maps {@code REDIS_URL} to {@code spring.data.redis.url} only when it is set.
 *
 * <p>This is not done in application.yml because a placeholder default can only be an empty
 * string, and Spring treats an empty Redis url as invalid rather than as absent - the application
 * would refuse to start wherever REDIS_URL is not provided, such as docker compose.
 */
public class RedisUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("REDIS_URL");
        if (StringUtils.hasText(url)) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("redisUrl", Map.of("spring.data.redis.url", url)));
        }
    }
}
