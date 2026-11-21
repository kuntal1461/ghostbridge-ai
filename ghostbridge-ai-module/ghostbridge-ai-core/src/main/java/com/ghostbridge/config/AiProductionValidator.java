package com.ghostbridge.config;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Fails startup unless the development-only mock provider runs in an explicitly approved non-production profile:
 * one of {@code local}, {@code dev} or {@code test} must be active <em>and</em> {@code prod} must not be active, so a
 * mixed profile list such as {@code local,prod} cannot bypass the restriction. No active profile is also rejected.
 */
public class AiProductionValidator implements InitializingBean {
    private static final Profiles APPROVED = Profiles.of(
            GhostBridgeAiLiterals.PROFILE_LOCAL, GhostBridgeAiLiterals.PROFILE_DEV, GhostBridgeAiLiterals.PROFILE_TEST);
    private static final Profiles PRODUCTION = Profiles.of(GhostBridgeAiLiterals.PROFILE_PROD);

    private final AiProperties properties;
    private final Environment environment;

    public AiProductionValidator(AiProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!GhostBridgeAiLiterals.PROVIDER_MOCK.equals(properties.provider())) {
            return;
        }
        if (environment.acceptsProfiles(PRODUCTION) || !environment.acceptsProfiles(APPROVED)) {
            throw new IllegalStateException("Unsafe AI configuration: ghostbridge.ai.provider=mock is allowed only when "
                    + "one of the profiles local, dev or test is active and prod is not");
        }
    }
}
