package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.SlugRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The slug shape rule, as a unit test with no Spring context (spec part 4).
 *
 * <p>The rule is {@code businesses_slug_shape} in V1__foundation.sql:
 * {@code slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'}. It is stated here as executable cases so that the two
 * things criteria 1.13 and 1.14 depend on are pinned down: what a generated slug is allowed to look
 * like, and what a collision suffix may therefore look like. A generator that produced any of the
 * rejected values below would be refused by the database at insert time.
 */
class SlugShapeTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "studio",
        "sunrise-barbers",
        "chez-anas-hair-beauty",
        "studio-2",
        "studio-2f3a",
        "a",
        "123",
        "4-you"
    })
    @DisplayName("accepts a lower-case kebab slug, including the collision suffixes 1.14 requires")
    void acceptsWellFormedSlugs(String slug) {
        assertThat(SlugRules.hasValidShape(slug)).as("slug %s", slug).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Studio",           // upper case: the normalisation must fold it
        "studio ",          // trailing space
        "sunrise barbers",  // space, not a hyphen
        "studio-",          // trailing hyphen, the naive result of stripping punctuation
        "-studio",          // leading hyphen, the naive result of a name starting with punctuation
        "studio--2",        // doubled hyphen, the naive result of joining an empty segment
        "studio_2",         // underscore
        "studio.2",         // dot
        "café",        // unfolded accent
        "ספר" // non-latin script that must be transliterated or replaced
    })
    @DisplayName("rejects the slugs a naive normalisation produces")
    void rejectsMalformedSlugs(String slug) {
        assertThat(SlugRules.hasValidShape(slug)).as("slug %s", slug).isFalse();
    }

    @Test
    @DisplayName("rejects an empty slug, so a name of pure punctuation cannot produce one")
    void rejectsEmptySlug() {
        assertThat(SlugRules.hasValidShape("")).isFalse();
        assertThat(SlugRules.hasValidShape(null)).isFalse();
    }
}
