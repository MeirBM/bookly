package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * Turn-4 criteria 4.11 and 4.13 — a business looks like itself, and the string it does that with is
 * owner-supplied text that ends up in an {@code <img src>} on a public page.
 *
 * <p>The contract under test:
 *
 * <pre>
 *   PUT /api/businesses/{businessId}/logo   {"logoUrl": "&lt;string or null&gt;"}
 *   200 -&gt; the business, whose body carries a logoUrl field
 *   400 -&gt; not an absolute http(s) URL
 * </pre>
 *
 * <p>Every set is asserted by reading the resource back rather than by trusting the response to the
 * write, and on <em>both</em> readers — the owner's {@code GET /api/businesses/{businessId}} and the
 * anonymous {@code GET /api/public/businesses/{slug}}. A write that answers 200 and stores nothing
 * passes the first check and fails the second, and the second is the one a customer experiences.
 *
 * <p>Written from {@code docs/spec/turn-4.md} and the V6 migration only; the author of this suite
 * has not read {@code backend/src/main}.
 */
class BusinessLogoIT extends ApiIntegrationTest {

    private static final String A_LOGO = "https://cdn.example.test/logos/first.png";
    private static final String ANOTHER_LOGO = "http://images.example.test/second.jpg";

    private record Fixture(Account owner, String businessId, String slug) {}

    /**
     * A business that is actually bookable — service, employee, the link and hours.
     *
     * <p>Turn-3 criterion 3.17 makes the public endpoint answer for a business with nothing to book
     * exactly as it answers for a slug that does not exist. A bare business would therefore be
     * invisible to the public half of 4.11 for a reason that has nothing to do with logos.
     */
    private Fixture aBusiness(String label) {
        Bookable bookable = newBookableBusiness("logo-" + label, 30);
        return new Fixture(bookable.owner(), bookable.businessId(), bookable.slug());
    }

    /** PUT /api/businesses/{businessId}/logo as the owner. */
    private ResponseEntity<String> setLogo(Fixture f, Object logoUrl) {
        return send(
                HttpMethod.PUT,
                businessPath(f.businessId(), "/logo"),
                body("logoUrl", logoUrl),
                f.owner().accessToken());
    }

    /** What the owner's own view of the business says the logo is. */
    private JsonNode asOwner(Fixture f) {
        ResponseEntity<String> response = get("/api/businesses/" + f.businessId(), f.owner().accessToken());
        assertThat(response.getStatusCode().value())
                .as("GET /api/businesses/{businessId} as its owner")
                .isEqualTo(200);
        return json(response);
    }

    /** What an anonymous customer arriving at the booking page sees. */
    private JsonNode asPublic(Fixture f) {
        ResponseEntity<String> response = publicBusiness(f.slug());
        assertThat(response.getStatusCode().value())
                .as("GET /api/public/businesses/{slug} with no token")
                .isEqualTo(200);
        return json(response);
    }

    /** A JSON field that is either absent or literally null holds no logo. */
    private static boolean carriesNoLogo(JsonNode business) {
        JsonNode logo = business.path("logoUrl");
        return logo.isMissingNode() || logo.isNull();
    }

    // -------------------------------------------------------------------- 4.11

    @Test
    @DisplayName("4.11 an owner can set a logo and it is carried by the authenticated and the public view")
    void ownerCanSetALogoAndBothViewsCarryIt() {
        Fixture f = aBusiness("set");

        assertThat(carriesNoLogo(asOwner(f)))
                .as("a business starts with no logo; 4.12 depends on the absence being visible as null")
                .isTrue();
        assertThat(carriesNoLogo(asPublic(f)))
                .as("and the public view of a logo-less business says so too")
                .isTrue();

        ResponseEntity<String> set = setLogo(f, A_LOGO);

        assertThat(set.getStatusCode().value())
                .as("PUT /logo with an absolute https URL")
                .isEqualTo(200);
        JsonNode written = json(set);
        assertThat(written.path("logoUrl").asText())
                .as("the response to the write is the business, carrying the logo just set")
                .isEqualTo(A_LOGO);
        assertThat(written.path("id").asText())
                .as("and it is the business that was addressed, not some other one")
                .isEqualTo(f.businessId());

        assertThat(asOwner(f).path("logoUrl").asText())
                .as("the owner reads the logo back — the dashboard half of 4.11")
                .isEqualTo(A_LOGO);
        assertThat(asPublic(f).path("logoUrl").asText())
                .as("and an anonymous customer sees it on the public booking page — the other half")
                .isEqualTo(A_LOGO);
    }

    @Test
    @DisplayName("4.11 a logo can be replaced by another")
    void aLogoCanBeReplaced() {
        Fixture f = aBusiness("replace");

        assertThat(setLogo(f, A_LOGO).getStatusCode().value()).as("the first logo").isEqualTo(200);
        ResponseEntity<String> replaced = setLogo(f, ANOTHER_LOGO);

        assertThat(replaced.getStatusCode().value())
                .as("PUT /logo over an existing logo — http is a scheme the criterion allows too")
                .isEqualTo(200);
        assertThat(json(replaced).path("logoUrl").asText()).isEqualTo(ANOTHER_LOGO);
        assertThat(asOwner(f).path("logoUrl").asText())
                .as("the second logo replaced the first rather than being added beside it")
                .isEqualTo(ANOTHER_LOGO);
        assertThat(asPublic(f).path("logoUrl").asText()).isEqualTo(ANOTHER_LOGO);
    }

    @Test
    @DisplayName("4.11 an owner can clear the logo with null, and both views then carry none")
    void ownerCanClearTheLogoWithNull() {
        Fixture f = aBusiness("clear-null");
        assertThat(setLogo(f, A_LOGO).getStatusCode().value()).as("setting the logo first").isEqualTo(200);

        ResponseEntity<String> cleared = setLogo(f, null);

        assertThat(cleared.getStatusCode().value())
                .as("PUT /logo with a null logoUrl clears it; clearing is not an error")
                .isEqualTo(200);
        assertThat(carriesNoLogo(json(cleared)))
                .as("the response to the clear reports no logo, not the one that was just removed")
                .isTrue();
        assertThat(carriesNoLogo(asOwner(f)))
                .as("the owner's view after clearing")
                .isTrue();
        assertThat(carriesNoLogo(asPublic(f)))
                .as("the public view after clearing — the customer must get 4.12's Bookly mark back")
                .isTrue();
    }

    @Test
    @DisplayName("4.11 a blank logoUrl clears the logo rather than storing an empty string")
    void aBlankLogoUrlClearsTheLogo() {
        SoftAssertions soft = new SoftAssertions();
        for (String blank : List.of("", "   ")) {
            Fixture f = aBusiness("clear-blank-" + blank.length());
            assertThat(setLogo(f, A_LOGO).getStatusCode().value()).as("setting the logo first").isEqualTo(200);

            ResponseEntity<String> cleared = setLogo(f, blank);

            soft.assertThat(cleared.getStatusCode().value())
                    .as("PUT /logo with a blank logoUrl (%s) clears it", blank.isEmpty() ? "empty" : "whitespace")
                    .isEqualTo(200);
            soft.assertThat(carriesNoLogo(json(cleared)))
                    .as("a cleared logo is null, never the empty string: \"\" in an <img src> is a "
                            + "request back to the page itself, which is 4.14's broken image")
                    .isTrue();
            soft.assertThat(carriesNoLogo(asOwner(f)))
                    .as("the owner's view after clearing with a blank string")
                    .isTrue();
            soft.assertThat(carriesNoLogo(asPublic(f)))
                    .as("the public view after clearing with a blank string")
                    .isTrue();
        }
        soft.assertAll();
    }

    @Test
    @DisplayName("4.11 clearing a logo that was never set is not an error")
    void clearingALogoThatWasNeverSetIsNotAnError() {
        Fixture f = aBusiness("clear-idempotent");

        ResponseEntity<String> cleared = setLogo(f, null);

        assertThat(cleared.getStatusCode().value())
                .as("clearing is stating a desired end state, and the end state is already true")
                .isEqualTo(200);
        assertThat(carriesNoLogo(asOwner(f))).isTrue();
    }

    // -------------------------------------------------------------------- 4.13

    /**
     * 4.13 — only {@code http(s)} URLs are accepted; a {@code javascript:} or {@code data:} URL is
     * refused with 400.
     *
     * <p>Pitfall 5 is the reason this criterion exists: the logo is owner-supplied text rendered on
     * a page anonymous customers visit. The refusal is asserted on the status code, and then on the
     * stored state — a 400 that nevertheless wrote the value would satisfy a status assertion and
     * still put {@code javascript:} into the public page.
     */
    @Test
    @DisplayName("4.13 refuses a URL that is not http(s) with 400")
    void refusesAUrlThatIsNotHttp() {
        record Rejected(String what, String url) {}
        List<Rejected> rejected = List.of(
                new Rejected("a javascript: URL, which executes when the browser resolves it",
                        "javascript:alert(1)"),
                new Rejected("a javascript: URL wearing mixed case, since a case-sensitive check is "
                                + "no check at all — the browser does not care",
                        "JaVaScRiPt:alert(document.domain)"),
                new Rejected("a data: URL, which can carry an SVG carrying a script",
                        "data:image/svg+xml;base64,PHN2Zy8+"),
                new Rejected("a data: URL declared as a plain image", "data:image/png;base64,iVBORw0KGgo="),
                new Rejected("a relative path, which is not an absolute URL at all", "/logo.png"),
                new Rejected("a deeper relative path", "../../etc/passwd"),
                new Rejected("a scheme-less string that looks like a host", "example.test/logo.png"),
                new Rejected("a protocol-relative URL, whose scheme is decided by the page not the owner",
                        "//example.test/logo.png"),
                new Rejected("a file: URL", "file:///etc/passwd"),
                new Rejected("an ftp: URL — the criterion is an allowlist of http and https, not a "
                                + "blocklist of the two schemes that were named",
                        "ftp://example.test/logo.png"));

        Fixture f = aBusiness("refuse");
        assertThat(setLogo(f, A_LOGO).getStatusCode().value())
                .as("a good logo is set first, so a refusal can be shown not to have overwritten it")
                .isEqualTo(200);

        SoftAssertions soft = new SoftAssertions();
        for (Rejected r : rejected) {
            ResponseEntity<String> response = setLogo(f, r.url());

            soft.assertThat(response.getStatusCode().value())
                    .as("%s (%s) is not an absolute http(s) URL and must be refused with 400",
                            r.what(), r.url())
                    .isEqualTo(400);
            soft.assertThat(asOwner(f).path("logoUrl").asText())
                    .as("%s: a refused write must leave the stored logo exactly as it was", r.what())
                    .isEqualTo(A_LOGO);
        }
        soft.assertAll();

        assertThat(asPublic(f).path("logoUrl").asText())
                .as("and after every refusal the public page still carries the logo that was legitimately set")
                .isEqualTo(A_LOGO);

        Integer stored = jdbc().queryForObject(
                "select count(*) from businesses where id = ?::uuid and logo_url = ?",
                Integer.class,
                f.businessId(),
                A_LOGO);
        assertThat(stored)
                .as("the column itself still holds the good URL and nothing that was refused")
                .isEqualTo(1);
    }
}
