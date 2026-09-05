package com.bookly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * Turn-1 criteria 1.6 and 1.7.
 *
 * <p>Pitfall 3: refresh rotation without reuse detection is a rotation in name only. A stolen token
 * that still works once leaves theft undetectable, so presenting a rotated token must revoke the
 * whole family — including the token the legitimate client is currently holding.
 */
class RefreshRotationIT extends ApiIntegrationTest {

    private ResponseEntity<String> refresh(String refreshToken) {
        return post("/api/auth/refresh", body("refreshToken", refreshToken));
    }

    /**
     * 1.6 — POST /api/auth/refresh returns a new token pair, and the presented refresh token is
     * thereafter rejected with 401.
     */
    @Test
    @DisplayName("1.6 a rotated refresh token is rejected when it is presented again")
    void rotatedTokenIsRejectedOnReuse() {
        Account account = newAccount("rotate");
        String first = account.refreshToken();

        ResponseEntity<String> rotated = refresh(first);

        assertThat(rotated.getStatusCode().value()).as("first refresh").isEqualTo(200);
        JsonNode pair = json(rotated);
        assertThat(pair.path("accessToken").asText()).as("rotated accessToken").isNotBlank();
        assertThat(pair.path("refreshToken").asText()).as("rotated refreshToken").isNotBlank();
        assertThat(pair.path("refreshToken").asText())
                .as("the refresh token must actually rotate")
                .isNotEqualTo(first);

        ResponseEntity<String> replay = refresh(first);

        assertThat(replay.getStatusCode().value())
                .as("presenting the already-rotated refresh token a second time")
                .isEqualTo(401);
    }

    /**
     * 1.7 — a refresh token reused after rotation revokes every token in <em>the family that token
     * belongs to</em>. A second login starts its own family and must be unaffected: revoking every
     * session a user has because one was replayed would log them out of devices that were never
     * compromised.
     */
    @Test
    @DisplayName("1.7 reusing a rotated refresh token revokes its family and only its family")
    void reuseRevokesFamily() {
        Account account = newAccount("family");
        String compromisedFirst = account.refreshToken();

        // A second, independent login: its own family, on another device.
        ResponseEntity<String> secondLogin = login(account.email(), account.password());
        assertThat(secondLogin.getStatusCode().value()).as("second login").isEqualTo(200);
        String otherFamily = json(secondLogin).path("refreshToken").asText();
        assertThat(otherFamily).as("the second family's token").isNotBlank().isNotEqualTo(compromisedFirst);

        String compromisedSecond = json(refresh(compromisedFirst)).path("refreshToken").asText();
        assertThat(compromisedSecond).as("the rotated token").isNotBlank();

        // The attacker replays the captured, already-rotated token.
        assertThat(refresh(compromisedFirst).getStatusCode().value())
                .as("replay of the captured token")
                .isEqualTo(401);

        // The legitimate holder of that family is now locked out too: that is what "the whole
        // family" means, and it is the only signal the user gets that the token was stolen.
        assertThat(refresh(compromisedSecond).getStatusCode().value())
                .as("the still-current token of the family in which reuse was detected")
                .isEqualTo(401);

        // The other family keeps working — revocation is per family, not per user.
        ResponseEntity<String> unaffected = refresh(otherFamily);
        assertThat(unaffected.getStatusCode().value())
                .as("the token of the family that was never replayed")
                .isEqualTo(200);
        assertThat(json(unaffected).path("refreshToken").asText())
                .as("the unaffected family still rotates normally")
                .isNotBlank();

        Integer liveFamilies = jdbc().queryForObject(
                "select count(distinct family_id) from refresh_tokens "
                        + "where user_id = ?::uuid and revoked_at is null",
                Integer.class,
                account.userId());
        assertThat(liveFamilies)
                .as("families of this user still holding an unrevoked token: the compromised one "
                        + "must be gone and the other must remain")
                .isEqualTo(1);
    }
}
