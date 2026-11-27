package com.jobhunter.users.application;

import com.jobhunter.auth.application.PasswordPolicy;
import com.jobhunter.shared.api.ApiException;
import com.jobhunter.shared.audit.AuditService;
import com.jobhunter.users.domain.ConsentType;
import com.jobhunter.users.domain.User;
import com.jobhunter.users.domain.UserConsent;
import com.jobhunter.users.infrastructure.UserConsentRepository;
import com.jobhunter.users.infrastructure.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserService {

    /** AI providers a consent can be granted to. */
    public static final List<String> AI_PROVIDERS = List.of("deepseek", "claude");
    private static final Set<String> THEMES = Set.of("light", "dark", "system");

    private final UserRepository users;
    private final UserConsentRepository consents;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;

    public UserService(UserRepository users, UserConsentRepository consents, PasswordEncoder passwordEncoder,
                       AuditService audit, Clock clock) {
        this.users = users;
        this.consents = consents;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public User get(long userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("Usuario"));
    }

    @Transactional
    public User updateSettings(long userId, String fullName, String locale, String timezone, String theme) {
        if (theme != null && !THEMES.contains(theme)) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Tema no válido");
        }
        if (timezone != null) {
            try {
                java.time.ZoneId.of(timezone);
            } catch (Exception e) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Zona horaria no válida");
            }
        }
        User user = get(userId);
        user.updateSettings(fullName, locale, timezone, theme);
        return user;
    }

    @Transactional
    public void changePassword(long userId, String current, String next) {
        User user = get(userId);
        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CREDENTIALS", "La contraseña actual no es correcta");
        }
        PasswordPolicy.validate(next);
        user.changePasswordHash(passwordEncoder.encode(next));
        audit.record(userId, "PASSWORD_CHANGED", "USER", userId, null);
    }

    public record ConsentView(String consentType, String provider, boolean granted, Instant grantedAt) {
    }

    public record ConsentChange(ConsentType consentType, String provider, boolean granted) {
    }

    @Transactional(readOnly = true)
    public List<ConsentView> consents(long userId) {
        Map<String, UserConsent> existing = new java.util.HashMap<>();
        consents.findByUserId(userId).forEach(c -> existing.put(c.getConsentType() + "|" + c.getProvider(), c));
        List<ConsentView> result = new ArrayList<>();
        for (ConsentType type : ConsentType.values()) {
            for (String provider : AI_PROVIDERS) {
                UserConsent c = existing.get(type + "|" + provider);
                result.add(new ConsentView(type.name(), provider, c != null && c.isGranted(),
                        c != null && c.isGranted() ? c.getGrantedAt() : null));
            }
        }
        return result;
    }

    @Transactional
    public List<ConsentView> updateConsents(long userId, List<ConsentChange> changes) {
        Instant now = clock.instant();
        for (ConsentChange change : changes) {
            if (!AI_PROVIDERS.contains(change.provider())) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Proveedor desconocido: " + change.provider());
            }
            UserConsent consent = consents.findByUserIdAndConsentTypeAndProvider(userId, change.consentType(), change.provider())
                    .orElseGet(() -> consents.save(new UserConsent(userId, change.consentType(), change.provider())));
            boolean before = consent.isGranted();
            consent.set(change.granted(), now);
            if (before != change.granted()) {
                audit.record(userId, "CONSENT_CHANGED", "CONSENT", change.consentType() + ":" + change.provider(),
                        Map.of("granted", change.granted()));
            }
        }
        return consents(userId);
    }

    /** Used by the AI gateway before sending any personal data to a provider. */
    @Transactional(readOnly = true)
    public boolean hasConsent(long userId, ConsentType type, String provider) {
        return consents.findByUserIdAndConsentTypeAndProvider(userId, type, provider)
                .map(UserConsent::isGranted).orElse(false);
    }
}
