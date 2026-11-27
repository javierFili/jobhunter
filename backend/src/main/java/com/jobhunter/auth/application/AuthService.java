package com.jobhunter.auth.application;

import com.jobhunter.auth.domain.RefreshToken;
import com.jobhunter.auth.infrastructure.RefreshTokenRepository;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.audit.AuditService;
import com.jobhunter.users.domain.Role;
import com.jobhunter.users.domain.User;
import com.jobhunter.users.infrastructure.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    /** Same message for unknown e-mail and wrong password (no account enumeration). */
    private static final String INVALID = "Correo o contraseña incorrectos";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SecurityProperties props;
    private final AuditService audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    /** Used to spend comparable time on unknown e-mails (timing-based enumeration). */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       TokenService tokenService, SecurityProperties props, AuditService audit, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.props = props;
        this.audit = audit;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /** Result of a successful authentication: the access token plus the raw refresh token for the cookie. */
    public record Session(User user, String accessToken, long expiresIn, String refreshToken) {
    }

    @Transactional
    public Session register(String email, String password, String fullName, String userAgent, String ip) {
        String normalized = User.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Ya existe una cuenta con ese correo");
        }
        PasswordPolicy.validate(password);
        // Single-user first: the first account administers the installation.
        Role role = users.count() == 0 ? Role.ADMIN : Role.USER;
        User user = users.save(new User(normalized, passwordEncoder.encode(password), fullName, role));
        audit.record(user.getId(), "REGISTER", "USER", user.getId(), Map.of("role", role.name()));
        return startSession(user, userAgent, ip);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session login(String email, String password, String userAgent, String ip) {
        Instant now = clock.instant();
        User user = users.findByEmail(User.normalizeEmail(email)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            audit.record(null, "LOGIN_FAILED", "USER", null, Map.of("reason", "unknown_email"));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID);
        }
        if (user.isLocked(now)) {
            audit.record(user.getId(), "LOGIN_FAILED", "USER", user.getId(), Map.of("reason", "locked"));
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Cuenta bloqueada temporalmente por intentos fallidos; inténtalo en 15 minutos");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.registerFailedLogin(now);
            audit.record(user.getId(), "LOGIN_FAILED", "USER", user.getId(), Map.of("reason", "bad_password"));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID);
        }
        user.registerSuccessfulLogin(now);
        audit.record(user.getId(), "LOGIN", "USER", user.getId(), null);
        return startSession(user, userAgent, ip);
    }

    /** Rotates the refresh token. Re-use of a rotated token revokes the whole family (likely theft). */
    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String rawToken, String userAgent, String ip) {
        Instant now = clock.instant();
        RefreshToken current = rawToken == null ? null : refreshTokens.findByTokenHash(hash(rawToken)).orElse(null);
        if (current == null) {
            throw unauthorized();
        }
        if (current.getRevokedAt() != null && current.getReplacedById() != null) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            audit.record(current.getUserId(), "REFRESH_TOKEN_REUSE", "USER", current.getUserId(),
                    Map.of("family", current.getFamilyId()));
            throw unauthorized();
        }
        if (!current.isActive(now)) {
            throw unauthorized();
        }
        User user = users.findById(current.getUserId()).orElseThrow(AuthService::unauthorized);
        if (user.isLocked(now)) {
            throw unauthorized();
        }
        String raw = newRawToken();
        RefreshToken next = refreshTokens.save(new RefreshToken(user.getId(), hash(raw), current.getFamilyId(),
                now.plus(props.refreshTokenTtl()), userAgent, ip));
        current.revoke(now, next.getId());
        return new Session(user, tokenService.issueAccessToken(user, now), tokenService.accessTokenTtlSeconds(), raw);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null) {
            return;
        }
        refreshTokens.findByTokenHash(hash(rawToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshTokens.revokeAllForUser(userId, clock.instant());
    }

    private Session startSession(User user, String userAgent, String ip) {
        Instant now = clock.instant();
        String raw = newRawToken();
        refreshTokens.save(new RefreshToken(user.getId(), hash(raw), UUID.randomUUID().toString(),
                now.plus(props.refreshTokenTtl()), userAgent, ip));
        return new Session(user, tokenService.issueAccessToken(user, now), tokenService.accessTokenTtlSeconds(), raw);
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] hash(String raw) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Sesión expirada; inicia sesión de nuevo");
    }
}
