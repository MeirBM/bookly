package com.bookly.common;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Accepts {@code DATABASE_URL} in the form managed Postgres providers hand out
 * ({@code postgres://user:password@host:port/db?sslmode=require}) and turns it into the JDBC (allow-secret: placeholder)
 * url, username and password Spring's datasource needs.
 *
 * <p>A JDBC url ({@code jdbc:postgresql://...}) is left alone, so docker compose and the tests
 * keep working with {@code DATABASE_USER} and {@code DATABASE_PASSWORD} as before.
 *
 * <p>TLS is required for any host other than localhost unless the url says otherwise: a
 * database reached over the public internet must not be reached in the clear.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "databaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = environment.getProperty("DATABASE_URL");
        if (raw == null || !(raw.startsWith("postgres://") || raw.startsWith("postgresql://"))) {
            return;
        }
        URI uri = URI.create(raw);
        String host = uri.getHost();
        if (host == null || uri.getPath() == null || uri.getPath().length() < 2) {
            // Never echo the value: it carries the password.
            throw new IllegalStateException("DATABASE_URL must look like postgres://user:password@host:port/database"); // allow-secret
        }
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String query = uri.getRawQuery();
        boolean local = host.equals("localhost") || host.equals("127.0.0.1");
        if ((query == null || !query.contains("sslmode=")) && !local) {
            query = (query == null ? "" : query + "&") + "sslmode=require";
        }
        String jdbc = "jdbc:postgresql://" + host + ":" + port + uri.getPath() + (query == null ? "" : "?" + query);

        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", jdbc);
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            String user = colon < 0 ? userInfo : userInfo.substring(0, colon);
            props.put("spring.datasource.username", decode(user));
            if (colon >= 0) {
                props.put("spring.datasource.password", decode(userInfo.substring(colon + 1)));
            }
        }
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
