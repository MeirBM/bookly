package com.bookly.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The routes the application actually registers, read from Spring at runtime.
 *
 * <p>Turn-1 spec, part 4: the tenant-isolation suite enumerates routes from
 * {@code RequestMappingHandlerMapping} rather than from a hand-maintained list, so a new
 * tenant-scoped route that nobody remembered to cover fails the build instead of passing unnoticed.
 */
public final class Routes {

    /** The path-variable name that marks a route as tenant-scoped. Spec part 3. */
    public static final String TENANT_PATH_VARIABLE = "{businessId}";

    private Routes() {}

    public record Route(HttpMethod method, String pattern) {
        @Override
        public String toString() {
            return method + " " + pattern;
        }

        public boolean tenantScoped() {
            return pattern.contains(TENANT_PATH_VARIABLE);
        }
    }

    /** Every application route (under {@code /api/}), one entry per HTTP method it answers. */
    public static List<Route> application(RequestMappingHandlerMapping mapping) {
        List<Route> routes = new ArrayList<>();
        mapping.getHandlerMethods().forEach((info, handler) -> {
            for (String pattern : patternsOf(info)) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                for (HttpMethod method : methodsOf(info)) {
                    routes.add(new Route(method, pattern));
                }
            }
        });
        routes.sort(Comparator.comparing(Route::pattern).thenComparing(r -> r.method().name()));
        return routes;
    }

    public static List<Route> tenantScoped(RequestMappingHandlerMapping mapping) {
        return application(mapping).stream().filter(Route::tenantScoped).toList();
    }

    /** Routes that require an authenticated caller: everything under /api/ except the auth endpoints. */
    public static List<Route> authenticated(RequestMappingHandlerMapping mapping) {
        return application(mapping).stream()
                .filter(r -> !r.pattern().startsWith("/api/auth/"))
                .toList();
    }

    private static Set<String> patternsOf(RequestMappingInfo info) {
        Set<String> patterns = new TreeSet<>();
        if (info.getPathPatternsCondition() != null) {
            patterns.addAll(info.getPathPatternsCondition().getPatternValues());
        }
        if (patterns.isEmpty() && info.getPatternsCondition() != null) {
            patterns.addAll(info.getPatternsCondition().getPatterns());
        }
        return patterns;
    }

    private static Set<HttpMethod> methodsOf(RequestMappingInfo info) {
        Set<RequestMethod> declared = info.getMethodsCondition().getMethods();
        Set<HttpMethod> methods = new java.util.LinkedHashSet<>();
        if (declared.isEmpty()) {
            // A mapping with no method condition answers every method; GET is enough to decide
            // whether the route is guarded.
            methods.add(HttpMethod.GET);
        } else {
            for (RequestMethod m : declared) {
                methods.add(HttpMethod.valueOf(m.name()));
            }
        }
        return methods;
    }

    /**
     * Fills a path template: {businessId} takes the given business, every other variable takes a
     * random UUID so that the request is well formed but names nothing that exists.
     */
    public static String fill(String pattern, String businessId) {
        String path = pattern.replace(TENANT_PATH_VARIABLE, businessId);
        return path.replaceAll("\\{[^/}]+}", java.util.UUID.randomUUID().toString());
    }
}
