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
 * <p>The classification is deliberately inverted: a route is <strong>checked by default</strong>.
 * Selecting tenant-scoped routes by looking for {@code {businessId}} in the path was the obvious
 * reading of the spec and it is a hole in the guardrail — {@code /api/appointments/{appointmentId}}
 * contains no {@code {businessId}}, so the suite would generate no cases for exactly the IDOR shape
 * it exists to catch and the build would stay green.
 *
 * <p>Turn 3 adds a third audience, so there are three categories rather than two. The public
 * booking surface is <em>not</em> an exemption: it carries its own expectations — reachable without
 * a token, rate limited by address, and disclosing nothing — and the suites generate a case per
 * route from {@link #publicSurface}. A route added under {@code /api/public/} is therefore still
 * held to something, and a route added anywhere else is still held to everything. The only way to
 * make a route invisible is to add its exact pattern to {@link #UNAUTHENTICATED_ENTRY_PATTERNS},
 * which is four lines long and reviewed.
 */
public final class Routes {

    /** The path variable that names a tenant directly. Turn-2 spec, part 3. */
    public static final String TENANT_PATH_VARIABLE = "{businessId}";

    /** Everything below this prefix is the anonymous booking surface. Turn-3 criteria 3.11-3.17. */
    public static final String PUBLIC_PREFIX = "/api/public/";

    /**
     * The unauthenticated entry points: there is no caller yet, so there is nothing to isolate.
     * Exact patterns, not a prefix — a new route under {@code /api/auth/} has to be added here on
     * purpose, having been thought about, rather than inheriting an exemption from where somebody
     * happened to put it.
     */
    public static final Set<String> UNAUTHENTICATED_ENTRY_PATTERNS = Set.of(
            "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    /** Who a route is for, which decides what is asserted about it. */
    public enum Category {
        /** No caller yet: login, register, refresh, logout. */
        UNAUTHENTICATED_ENTRY,
        /** Anonymous by design: reachable without a token, limited by address, discloses nothing. */
        PUBLIC_SURFACE,
        /** The default, and what an unrecognised route becomes: authenticated and tenant-isolated. */
        TENANT_SCOPED
    }

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

    public static Category categoryOf(Route route) {
        if (UNAUTHENTICATED_ENTRY_PATTERNS.contains(route.pattern())) {
            return Category.UNAUTHENTICATED_ENTRY;
        }
        if (route.pattern().startsWith(PUBLIC_PREFIX)) {
            return Category.PUBLIC_SURFACE;
        }
        return Category.TENANT_SCOPED;
    }

    /** True when the route is reachable without a token: an entry point or the public surface. */
    public static boolean isAnonymous(Route route) {
        return categoryOf(route) != Category.TENANT_SCOPED;
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
     * Every route that must be evidenced as isolated: everything that is neither an entry point nor
     * part of the public surface. The default is coverage; invisibility has to be asked for.
     */
    public static List<Route> requiringIsolationCoverage(RequestMappingHandlerMapping mapping) {
        return inCategory(mapping, Category.TENANT_SCOPED);
    }

    /** Routes requiring a token: the same set, since every tenant-scoped route is authenticated. */
    public static List<Route> authenticated(RequestMappingHandlerMapping mapping) {
        return inCategory(mapping, Category.TENANT_SCOPED);
    }

    /**
     * The anonymous booking surface. Not an exemption but its own obligation: the suites generate a
     * case per route here too, so a route added under the prefix is still held to the expectations
     * of the audience it was put in front of.
     */
    public static List<Route> publicSurface(RequestMappingHandlerMapping mapping) {
        return inCategory(mapping, Category.PUBLIC_SURFACE);
    }

    public static List<Route> inCategory(RequestMappingHandlerMapping mapping, Category category) {
        return application(mapping).stream().filter(r -> categoryOf(r) == category).toList();
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
        return fill(pattern, java.util.Map.of("businessId", businessId));
    }

    /**
     * Fills a path template from the given values; any variable with no value takes a random UUID,
     * so the request is well formed and names nothing the caller could be entitled to.
     */
    public static String fill(String pattern, java.util.Map<String, String> values) {
        java.util.regex.Matcher variables =
                java.util.regex.Pattern.compile("\\{([^/}]+)}").matcher(pattern);
        StringBuilder filled = new StringBuilder();
        while (variables.find()) {
            String name = variables.group(1);
            String value = values.getOrDefault(name, java.util.UUID.randomUUID().toString());
            variables.appendReplacement(filled, java.util.regex.Matcher.quoteReplacement(value));
        }
        variables.appendTail(filled);
        return filled.toString();
    }
}
