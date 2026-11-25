package com.ghostbridge.config;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.constants.GhostBridgeAppLiterals;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Fails startup when no account is configured outside the development profiles: an application nobody can sign in to
 * would otherwise start "successfully" and answer 401 to everyone.
 */
public class AppSecurityValidator implements InitializingBean {
    private static final Profiles DEVELOPMENT = Profiles.of(GhostBridgeAiLiterals.PROFILE_LOCAL,
            GhostBridgeAiLiterals.PROFILE_DEV, GhostBridgeAiLiterals.PROFILE_TEST);
    private static final Profiles PRODUCTION = Profiles.of(GhostBridgeAiLiterals.PROFILE_PROD);

    private static final String SECURE_COOKIE_PROPERTY = "server.servlet.session.cookie.secure";

    private final AppSecurityProperties properties;
    private final Environment environment;

    public AppSecurityValidator(AppSecurityProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        boolean development = environment.acceptsProfiles(DEVELOPMENT) && !environment.acceptsProfiles(PRODUCTION);
        if (!development && properties.users().isEmpty()) {
            throw new IllegalStateException("Unsafe configuration: at least one user must be configured in "
                    + GhostBridgeAppLiterals.SECURITY_PROPERTY_PREFIX + ".users outside the local, dev and test profiles");
        }
        boolean production = environment.acceptsProfiles(PRODUCTION);
        if (production && Boolean.FALSE.equals(environment.getProperty(SECURE_COOKIE_PROPERTY, Boolean.class))) {
            throw new IllegalStateException("Unsafe configuration: " + SECURE_COOKIE_PROPERTY + " must not be false in the prod profile");
        }
    }
}
