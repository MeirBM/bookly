package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * Turn-1 criterion 1.24: the OpenAPI document and Swagger UI are not served unless explicitly
 * enabled, via {@code bookly.security.expose-api-docs}, which defaults to false.
 *
 * <p>Both directions are asserted from the same file. A test that only checked the disabled case
 * would pass just as well against an application that served the document to nobody, and the shared
 * test profile enables it — which is why {@code OpenApiIT} can read it at all.
 *
 * <p>Why it matters: the document is a complete map of the API — every route, every parameter,
 * every schema — and Swagger UI is a working client for it. Neither is a vulnerability by itself,
 * and neither should be handed to an anonymous caller on a public deployment either.
 */
class ApiDocsExposureIT {

    private static final String API_DOCS = "/v3/api-docs";
    private static final String SWAGGER_UI = "/swagger-ui/index.html";
    private static final String SWAGGER_UI_ALIAS = "/swagger-ui.html";

    @Nested
    @TestPropertySource(properties = "bookly.security.expose-api-docs=true")
    @DisplayName("when the property enables it")
    class WhenExplicitlyEnabled extends ApiIntegrationTest {

        @Test
        @DisplayName("1.24 the document is served")
        void documentIsServed() {
            ResponseEntity<String> response = get(API_DOCS, null);

            assertThat(response.getStatusCode().value()).as("GET %s", API_DOCS).isEqualTo(200);
            assertThat(json(response).path("openapi").asText())
                    .as("the OpenAPI version the document declares")
                    .isNotBlank();
        }

        @Test
        @DisplayName("1.24 Swagger UI is reachable")
        void swaggerUiIsReachable() {
            ResponseEntity<String> response = get(SWAGGER_UI, null);

            assertThat(response.getStatusCode().value())
                    .as("GET %s — served directly or redirected, but reachable", SWAGGER_UI)
                    .isBetween(200, 399);
        }
    }

    @Nested
    @TestPropertySource(properties = "bookly.security.expose-api-docs=false")
    @DisplayName("when the property does not enable it")
    class WhenNotEnabled extends ApiIntegrationTest {

        @Test
        @DisplayName("1.24 the document is refused")
        void documentIsRefused() {
            ResponseEntity<String> response = get(API_DOCS, null);

            assertThat(response.getStatusCode().value())
                    .as("GET %s with the API map switched off", API_DOCS)
                    .isIn(401, 403, 404);
            assertThat(String.valueOf(response.getBody()))
                    .as("and the body must not be the document itself")
                    .doesNotContain("\"openapi\"", "\"paths\"");
        }

        @Test
        @DisplayName("1.24 Swagger UI is refused")
        void swaggerUiIsRefused() {
            for (String path : new String[] {SWAGGER_UI, SWAGGER_UI_ALIAS}) {
                ResponseEntity<String> response = get(path, null);

                assertThat(response.getStatusCode().value())
                        .as("GET %s with the API map switched off", path)
                        .isIn(401, 403, 404);
            }
        }

        @Test
        @DisplayName("1.24 switching the document off does not switch the API off")
        void theApiItselfStillWorks() {
            Account account = newAccount("docs-off");

            assertThat(get("/api/businesses", account.accessToken()).getStatusCode().value())
                    .as("an authenticated route while the document is not served")
                    .isEqualTo(200);
        }
    }
}
