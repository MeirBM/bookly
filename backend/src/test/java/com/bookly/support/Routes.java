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
 *
 * <p>The classification is deliberately inverted: <strong>every</strong> route under {@code /api/}
 * requires isolation coverage unless its exact path is on {@link #PUBLIC_PATTERNS}. Selecting
 * tenant-scoped routes by looking for {@code {businessId}} in the path was the obvious reading of
 * spec part 3 and it is a hole in the guardrail — {@code /api/appointments/{appointmentId}} and
 * {@code /api/employees/{employeeId}} contain no {@code {businessId}}, so the suite would generate
 * no cases for exactly the IDOR shape it exists to catch and the build would stay green. A new
 * route now defaults to being checked; exempting one is a deliberate edit to the list below.
 */
public final class Routes {

    /** The path variable that names a tenant directly. Spec part 3. */
    public static final String TENANT_PATH_VARIABLE = "{businessId}";

    /**
     * The only routes exempt from isolation coverage: the unauthenticated entry points, which have
     * no caller to isolate yet. Exact patterns, not a prefix — a new route under {@code /api/auth/}
     * has to be added here on purpose, having been thought about, rather than inheriting an
     * exemption from where somebody happened to put it.
     */
    public static final Set<String> PUBLIC_PATTERNS = Set.of(
            "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    private Routes() {}

    public record Route(HttpMethod method, String pattern) {
        @Override
        public String toString() {
            return method + " " + pattern;
        }

        /** Names a tenant directly. Kept for reporting; it is no longer what selects the cases. */
        public boolean namesABusinessInThePath() {
            return pattern.contains(TENANT_PATH_VARIABLE);
        }

        /**
         * Carries a path variable, so it can be addressed at a resource belonging to someone else.
         * That is the shape an isolation case can fire at directly.
         */
        public boolean addressableByResourceId() {
            return pattern.contains("{");
        }
    }

    public static boolean isPublic(Route route) {
        return PUBLIC_PATTERNS.contains(route.pattern());
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

    /**
     * Every route that must be evidenced as isolated: all of {@code /api/} except the public
     * allowlist. The default is coverage; invisibility has to be asked for.
     */
    public static List<Route> requiringIsolationCoverage(RequestMappingHandlerMapping mapping) {
        return application(mapping).stream().filter(r -> !isPublic(r)).toList();
    }

    /** Routes requiring a token: the same set, since every non-public route is authenticated. */
    public static List<Route> authenticated(RequestMappingHandlerMapping mapping) {
        return requiringIsolationCoverage(mapping);
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
