package com.bookly.business;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Turns a business name into the public identifier in {@code /book/{slug}}.
 *
 * <p>Pure and deterministic apart from the caller-supplied availability test, so it unit-tests
 * without a database.
 */
public final class SlugGenerator {

    private static final int MAX_LENGTH = 60;
    private static final int MAX_ATTEMPTS = 1000;

    private SlugGenerator() {
    }

    /**
     * @param isTaken decides whether a candidate is already in use
     * @return the base slug, or the first free {@code base-2}, {@code base-3}, ... Two businesses
     *         named "Studio" therefore both get a slug instead of one failing on the unique index.
     */
    public static String generate(String name, Predicate<String> isTaken) {
        String base = normalise(name);
        if (base.isEmpty()) {
            base = "business";
        }
        if (!isTaken.test(base)) {
            return base;
        }
        for (int suffix = 2; suffix < MAX_ATTEMPTS; suffix++) {
            String candidate = base + "-" + suffix;
            if (!isTaken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No free slug for name: " + name);
    }

    /** Lowercase ASCII words joined by single hyphens, matching the CHECK constraint in V1. */
    static String normalise(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String hyphenated = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (hyphenated.length() <= MAX_LENGTH) {
            return hyphenated;
        }
        return hyphenated.substring(0, MAX_LENGTH).replaceAll("-+$", "");
    }
}
