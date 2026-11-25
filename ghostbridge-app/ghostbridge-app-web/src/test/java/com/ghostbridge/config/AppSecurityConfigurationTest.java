package com.ghostbridge.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppSecurityConfigurationTest {
    private static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder(4).encode("test-pass-12");

    private static AppSecurityProperties bind(String... pairs) {
        Map<String, String> source = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            source.put("ghostbridge.app.security." + pairs[i], pairs[i + 1]);
        }
        return new Binder(new MapConfigurationPropertySource(source)).bindOrCreate("ghostbridge.app.security", AppSecurityProperties.class);
    }

    @Test
    void validUsersBindAndNeverPrintTheirHash() {
        AppSecurityProperties properties = bind("users[0].username", "demo", "users[0].password-hash", HASH, "users[0].roles[0]", "AI_USER");
        assertThat(properties.users()).hasSize(1);
        assertThat(properties.users().get(0).toString()).contains("demo").doesNotContain("bcrypt").doesNotContain(HASH);
        assertThat(properties.loginAttempts().maxFailures()).isEqualTo(5);
        assertThat(properties.loginAttempts().maxFailuresPerAddress()).isEqualTo(30);
    }

    @Test
    void plainTextNoopAndMalformedHashesAreRejected() {
        for (String bad : new String[]{"plain-password", "{noop}plain", "{bcrypt}short", "$2a$10$" + "a".repeat(53), ""}) {
            assertThatThrownBy(() -> bind("users[0].username", "demo", "users[0].password-hash", bad, "users[0].roles[0]", "AI_USER"))
                    .isInstanceOf(BindException.class);
        }
    }

    @Test
    void invalidUserNamesRolesAndDuplicatesAreRejected() {
        assertThatThrownBy(() -> bind("users[0].username", "bad name", "users[0].password-hash", HASH, "users[0].roles[0]", "AI_USER"))
                .isInstanceOf(BindException.class);
        assertThatThrownBy(() -> bind("users[0].username", "demo", "users[0].password-hash", HASH)).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> bind("users[0].username", "demo", "users[0].password-hash", HASH, "users[0].roles[0]", "bad role!"))
                .isInstanceOf(BindException.class);
        assertThatThrownBy(() -> bind("users[0].username", "Demo", "users[0].password-hash", HASH, "users[0].roles[0]", "AI_USER",
                "users[1].username", "demo", "users[1].password-hash", HASH, "users[1].roles[0]", "AI_USER")).isInstanceOf(BindException.class);
    }

    @Test
    void loginAttemptSettingsAreBounded() {
        assertThatThrownBy(() -> bind("login-attempts.max-failures", "0")).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> bind("login-attempts.window", "0s")).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> bind("login-attempts.window", "2d")).isInstanceOf(BindException.class);
    }

    private static AppSecurityValidator validator(AppSecurityProperties properties, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new AppSecurityValidator(properties, environment);
    }

    @Test
    void outsideTheDevelopmentProfilesAtLeastOneUserIsRequired() {
        AppSecurityProperties none = bind();
        AppSecurityProperties one = bind("users[0].username", "demo", "users[0].password-hash", HASH, "users[0].roles[0]", "AI_USER");
        assertThatThrownBy(() -> validator(none, "prod").afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validator(none, "qa").afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validator(none, "dev", "prod").afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validator(none).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
        validator(none, "dev").afterPropertiesSet();
        validator(none, "local").afterPropertiesSet();
        validator(none, "test").afterPropertiesSet();
        validator(one, "prod").afterPropertiesSet();
    }

    @Test
    void productionRefusesAnInsecureSessionCookie() {
        AppSecurityProperties one = bind("users[0].username", "demo", "users[0].password-hash", HASH, "users[0].roles[0]", "AI_USER");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("server.servlet.session.cookie.secure", "false");
        AppSecurityValidator insecure = new AppSecurityValidator(one, environment);
        assertThatThrownBy(insecure::afterPropertiesSet).isInstanceOf(IllegalStateException.class).hasMessageContaining("cookie.secure");
    }
}
