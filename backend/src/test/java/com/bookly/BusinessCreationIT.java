package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookly.support.ApiIntegrationTest;
import com.bookly.support.SlugRules;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

/** Turn-1 criteria 1.13 and 1.14. */
class BusinessCreationIT extends ApiIntegrationTest {

    /**
     * 1.13 — POST /api/businesses creates a business, makes the creator its BUSINESS_OWNER, and
     * assigns a unique slug.
     */
    @Test
    @DisplayName("1.13 the creator of a business becomes its BUSINESS_OWNER and it gets a slug")
    void creatorBecomesOwner() {
        Account creator = newAccount("founder");

        ResponseEntity<String> created = post(
                "/api/businesses",
                body("name", "Sunrise Barbers", "timezone", "Europe/London"),
                creator.accessToken());

        assertThat(created.getStatusCode().value()).as("POST /api/businesses").isEqualTo(201);
        JsonNode business = json(created);
        String businessId = business.path("id").asText();
        assertThat(businessId).as("BusinessResponse.id").isNotBlank();
        assertThat(business.path("name").asText()).as("BusinessResponse.name").isEqualTo("Sunrise Barbers");
        assertThat(business.path("timezone").asText()).as("BusinessResponse.timezone").isEqualTo("Europe/London");

        String slug = business.path("slug").asText();
        assertThat(slug).as("BusinessResponse.slug").isNotBlank();
        assertThat(slug)
                .as("the slug must satisfy businesses_slug_shape from V1__foundation.sql")
                .matches(SlugRules.SHAPE);

        // The membership row is what every later tenant decision is made from, so it is asserted
        // in the database rather than inferred from the response.
        String role = jdbc().queryForObject(
                "select role from business_members where business_id = ?::uuid and user_id = ?::uuid",
                String.class,
                businessId,
                creator.userId());
        assertThat(role).as("the creator's role in the business they created").isEqualTo("BUSINESS_OWNER");

        Integer members = jdbc().queryForObject(
                "select count(*) from business_members where business_id = ?::uuid", Integer.class, businessId);
        assertThat(members).as("members of a freshly created business").isEqualTo(1);

        // And the creator can immediately read it back.
        ResponseEntity<String> readBack = get("/api/businesses/" + businessId, creator.accessToken());
        assertThat(readBack.getStatusCode().value())
                .as("the owner reading their own business")
                .isEqualTo(200);
        assertThat(json(readBack).path("id").asText()).isEqualTo(businessId);
    }

    /**
     * 1.14 — two businesses cannot hold the same slug. Part 3 fixes what the second one gets
     * instead: the collision suffixes are {@code -2}, {@code -3}, and so on.
     */
    @Test
    @DisplayName("1.14 two businesses created with the same name do not share a slug")
    void slugIsUnique() {
        Account first = newAccount("twin-a");
        Account second = newAccount("twin-b");
        // A name unique to this run, so the expected suffix cannot depend on what an earlier run
        // left in the database.
        String token = java.util.UUID.randomUUID().toString().substring(0, 8);
        String sameName = "Studio " + token;

        JsonNode a = newBusiness(first, sameName);
        JsonNode b = newBusiness(second, sameName);

        String slugA = a.path("slug").asText();
        String slugB = b.path("slug").asText();
        assertThat(slugA).as("first slug").matches(SlugRules.SHAPE).isEqualTo("studio-" + token);
        assertThat(slugB)
                .as("the second business named %s takes the -2 collision suffix", sameName)
                .matches(SlugRules.SHAPE)
                .isEqualTo("studio-" + token + "-2");
        assertThat(slugB).as("the two slugs must differ").isNotEqualTo(slugA);

        Integer duplicated = jdbc().queryForObject(
                "select count(*) from (select slug from businesses group by slug having count(*) > 1) d",
                Integer.class);
        assertThat(duplicated).as("slugs held by more than one business").isZero();
    }

    /**
     * 1.13, and the normalisation part 3 now defines: strip accents to ASCII, lowercase, replace
     * every run of non-alphanumeric characters with a single hyphen, trim leading and trailing
     * hyphens, truncate to 60 characters; a name that normalises to nothing becomes
     * {@code business}.
     *
     * <p>Each expectation allows a trailing collision suffix, because part 3 says a collision takes
     * one and this test cannot know what an earlier run left behind.
     */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "'Sunrise Barbers', sunrise-barbers",
        "'Caf\u00e9 Cr\u00e8me', cafe-creme",
        "'ANA''S HAIR & BEAUTY', ana-s-hair-beauty",
        "'  --Studio Zen--  ', studio-zen",
        "'Studio   99', studio-99"
    })
    @DisplayName("1.13 the slug follows the normalisation rules in part 3")
    void slugFollowsTheNormalisationRules(String name, String expected) {
        Account owner = newAccount("slug");

        String slug = newBusiness(owner, name).path("slug").asText();

        assertThat(slug)
                .as("the slug of \"%s\"", name)
                .matches(SlugRules.SHAPE)
                .matches("^" + java.util.regex.Pattern.quote(expected) + "(-\\d+)?$");
    }

    /** 1.13 — a name that normalises to nothing becomes {@code business} (part 3). */
    @Test
    @DisplayName("1.13 a name in a non-Latin script becomes the neutral slug")
    void nameThatNormalisesToNothingBecomesBusiness() {
        Account owner = newAccount("neutral");

        String slug = newBusiness(owner, "\u05e1\u05e4\u05e8").path("slug").asText();

        assertThat(slug)
                .as("part 3: transliteration is not attempted, the slug becomes \"business\"")
                .matches(SlugRules.SHAPE)
                .matches("^business(-\\d+)?$");
    }

    /** 1.13 — the slug is truncated to 60 characters (part 3), before any collision suffix. */
    @Test
    @DisplayName("1.13 a long name is truncated to sixty characters")
    void longNameIsTruncatedToSixtyCharacters() {
        Account owner = newAccount("long");
        // Six-letter words, so that the 60-character boundary falls inside a word rather than on a
        // hyphen: truncating onto a hyphen would produce a slug the V1 CHECK constraint rejects.
        String name = String.join(" ", java.util.Collections.nCopies(12, "abcdef"));
        String normalised = String.join("-", java.util.Collections.nCopies(12, "abcdef"));
        String expected = normalised.substring(0, 60);

        String slug = newBusiness(owner, name).path("slug").asText();

        assertThat(slug)
                .as("the slug of a %d-character name", name.length())
                .matches(SlugRules.SHAPE)
                .matches("^" + java.util.regex.Pattern.quote(expected) + "(-\\d+)?$");
    }

    /**
     * The shape rule is a database CHECK constraint in V1__foundation.sql, not only a convention in
     * Java. A path that bypasses the service layer must still be unable to write a malformed slug.
     */
    @Test
    @DisplayName("the schema itself refuses a malformed slug")
    void schemaRejectsAMalformedSlug() {
        assertThatThrownBy(() -> jdbc().update(
                        "insert into businesses (name, slug, timezone) values (?, ?, ?)",
                        "Malformed",
                        "Not A Slug",
                        "Europe/London"))
                .as("businesses_slug_shape must reject a slug that is not lower-kebab-case")
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
