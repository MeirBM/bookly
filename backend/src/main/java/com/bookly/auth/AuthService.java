package com.bookly.auth;

import com.bookly.auth.dto.LoginRequest;
import com.bookly.auth.dto.RegisterRequest;
import com.bookly.auth.dto.TokenPairResponse;
import com.bookly.auth.dto.UserResponse;
import com.bookly.common.error.ApiException;
import com.bookly.user.User;
import com.bookly.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int REFRESH_TOKEN_BYTES = 32;

    /**
     * A valid BCrypt hash of a value nobody knows. Verified against when no user matches, so that
     * an unknown email costs the same time as a wrong password — otherwise response latency
     * enumerates registered accounts, and criterion 1.5 is satisfied only on paper.
     *
     * <p>Derived at startup from the injected encoder rather than hardcoded. BCrypt takes its cost
     * from the hash it is verifying, not from the encoder's configured strength, so a hardcoded
     * cost-10 constant would silently stop matching the moment anyone raised
     * {@code bookly.security.bcrypt-strength} — and the timing signal this exists to remove would
     * return, through a config change with no visible connection to this file.
     */
    private final String dummyHash;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       RefreshTokenRevoker refreshTokenRevoker,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenRevoker = refreshTokenRevoker;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "That email is already registered.");
        }
        try {
            User saved = userRepository.saveAndFlush(new User(
                    email,
                    passwordEncoder.encode(request.password()),
                    request.fullName().trim()));
            log.info("Registered user {}", saved.getId());
            return new UserResponse(saved.getId(), saved.getEmail(), saved.getFullName());
        } catch (DataIntegrityViolationException ex) {
            // Two registrations for the same email raced past the check above. The unique index
            // on lower(email) is what actually decides; this turns its violation into the same
            // answer the loser would have got a millisecond earlier.
            throw ApiException.conflict("EMAIL_TAKEN", "That email is already registered.");
        }
    }

    /**
     * Deliberately not {@code @Transactional}. Hibernate takes a pooled connection at the first
     * query and holds it until commit, which would span the BCrypt verification below — ~100ms by
     * design. With Hikari's default pool of ten, a few dozen concurrent unauthenticated login
     * attempts would exhaust the pool and stall every other request in the application, including
     * the membership lookup every tenant-scoped read depends on. The only write here is the token
     * insert, which manages its own transaction.
     */
    public TokenPairResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            // Logged without the email: an attacker's guesses would otherwise write a list of
            // addresses into the log, and the operator only needs to see that it is happening.
            log.info("Failed login for unknown account");
            throw ApiException.invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.info("Failed login for user {}", user.getId());
            throw ApiException.invalidCredentials();
        }
        return issueTokenPair(user, UUID.randomUUID());
    }

    /**
     * Rotates a refresh token.
     *
     * <p>Presenting a token that was already rotated means it was captured — the legitimate holder
     * would be using its replacement. Since the two cannot be told apart, the whole family is
     * revoked and both are forced to log in again. Rotation without this check is rotation in name
     * only: a stolen token would keep working once, undetected.
     */
    @Transactional
    public TokenPairResponse refresh(String presentedToken) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(presentedToken))
                .orElseThrow(ApiException::invalidRefreshToken);

        if (stored.isRevoked()) {
            // Committed in its own transaction: the throw below would otherwise roll the
            // revocation back, leaving the stolen token's replacement working.
            int revoked = refreshTokenRevoker.revokeFamily(stored.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {}; revoked {} tokens in family {}",
                    stored.getUserId(), revoked, stored.getFamilyId());
            throw ApiException.invalidRefreshToken();
        }
        if (stored.isExpiredAt(now)) {
            throw ApiException.invalidRefreshToken();
        }

        stored.revokeAt(now);
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(ApiException::invalidRefreshToken);
        return issueTokenPair(user, stored.getFamilyId());
    }

    @Transactional
    public void logout(String presentedToken) {
        // Unlocked read: logout revokes the family outright, so there is no check-then-act to race.
        refreshTokenRepository.findFirstByTokenHash(hash(presentedToken)).ifPresent(
                token -> refreshTokenRevoker.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private TokenPairResponse issueTokenPair(User user, UUID familyId) {
        Instant now = clock.instant();
        byte[] raw = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(raw);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        refreshTokenRepository.save(new RefreshToken(
                user.getId(), familyId, hash(refreshToken),
                now.plus(jwtProperties.refreshTokenTtl())));

        return new TokenPairResponse(
                jwtService.createAccessToken(user.getId(), user.getEmail()),
                refreshToken,
                jwtService.accessTokenTtl().toSeconds());
    }

    /** Stores a digest rather than the token, so the table is not a set of usable credentials. */
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by every JVM", ex);
        }
    }
}
