package com.bookly.support;

import java.util.regex.Pattern;

/**
 * The slug contract, transcribed from the two places the specification states it.
 *
 * <ul>
 *   <li>{@code businesses_slug_shape} in {@code V1__foundation.sql}:
 *       {@code slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'} — the schema is part of the contract, so this
 *       shape is binding on anything the application generates.
 *   <li>Turn-1 criterion 1.14 and pitfall 7: the slug is derived from the business name by a
 *       deterministic normalisation, and collisions are resolved with a suffix.
 * </ul>
 *
 * <p>What the specification does <em>not</em> state is how punctuation normalises — whether
 * "Ana's" becomes {@code anas} or {@code ana-s} — so nothing here assumes an answer, and the tests
 * that use it only rely on names where every reasonable normalisation agrees.
 */
public final class SlugRules {

    /** Exactly the regular expression the V1 CHECK constraint applies. */
    public static final String SHAPE = "^[a-z0-9]+(-[a-z0-9]+)*$";

    private static final Pattern PATTERN = Pattern.compile(SHAPE);

    private SlugRules() {}

    public static boolean hasValidShape(String slug) {
        return slug != null && PATTERN.matcher(slug).matches();
    }
}
