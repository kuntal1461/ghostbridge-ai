package com.ghostbridge.config;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.constants.GhostBridgeAppLiterals;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Authentication settings of the App ({@code ghostbridge.app.security.*}). Users come from the runtime environment or a
 * secret store: the committed configuration contains none. A password is accepted only as a bcrypt hash
 * ({@code {bcrypt}$2...}), never as plain text.
 */
@Validated
@ConfigurationProperties(prefix = GhostBridgeAppLiterals.SECURITY_PROPERTY_PREFIX)
public record AppSecurityProperties(
        @Valid List<User> users,
        @Valid @NotNull @DefaultValue LoginAttempts loginAttempts) {
    private static final Pattern USERNAME = Pattern.compile(GhostBridgeAppLiterals.REGEX_USERNAME);
    private static final Pattern ROLE = Pattern.compile(GhostBridgeAiLiterals.REGEX_ROLE_NAME);
    private static final Pattern BCRYPT_HASH = Pattern.compile(Pattern.quote(GhostBridgeAppLiterals.PASSWORD_HASH_PREFIX) + "\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    public AppSecurityProperties {
        users = users == null ? List.of() : List.copyOf(users);
        Set<String> seen = new HashSet<>();
        for (User user : users) {
            if (!seen.add(user.username().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(GhostBridgeAppLiterals.SECURITY_PROPERTY_PREFIX + ".users contains a duplicate username");
            }
        }
    }

    /** A configured account. {@code toString} omits the hash. */
    public record User(String username, String passwordHash, List<String> roles) {
        public User {
            if (username == null || !USERNAME.matcher(username).matches()) {
                throw new IllegalArgumentException(GhostBridgeAppLiterals.SECURITY_PROPERTY_PREFIX + ".users[].username is invalid");
            }
            if (passwordHash == null || !BCRYPT_HASH.matcher(passwordHash).matches()) {
                throw new IllegalArgumentException(GhostBridgeAppLiterals.SECURITY_PROPERTY_PREFIX
                        + ".users[].password-hash must be a {bcrypt} hash");
            }
            roles = roles == null ? List.of() : List.copyOf(roles);
            if (roles.isEmpty() || roles.stream().anyMatch(role -> role == null || !ROLE.matcher(role).matches())) {
                throw new IllegalArgumentException(GhostBridgeAppLiterals.SECURITY_PROPERTY_PREFIX + ".users[].roles must be valid role names");
            }
        }

        @Override
        public String toString() {
            return "User[username=" + username + ", roles=" + roles + "]";
        }
    }

    /**
     * Failed-login throttling. A user name is blocked after {@code maxFailures} failures; a client address, which may be a
     * shared office or NAT address, only after the higher {@code maxFailuresPerAddress}. Blocks last until {@code window} ends.
     */
    public record LoginAttempts(
            @DefaultValue("5") @Min(1) @Max(1000) int maxFailures,
            @DefaultValue("30") @Min(1) @Max(100000) int maxFailuresPerAddress,
            @DefaultValue("15m") @NotNull Duration window) {
        public LoginAttempts {
            if (maxFailures < 1 || maxFailuresPerAddress < 1) {
                throw new IllegalArgumentException(GhostBridgeAppLiterals.SECURITY_PROPERTY_PREFIX + ".login-attempts thresholds must be at least 1");
            }
            if (window == null || window.isZero() || window.isNegative() || window.compareTo(Duration.ofDays(1)) > 0) {
                throw new IllegalArgumentException(GhostBridgeAppLiterals.SECURITY_PROPERTY_PREFIX + ".login-attempts.window must be between 1ms and 1 day");
            }
        }
    }
}
