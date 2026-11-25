package com.ghostbridge.security;

import com.ghostbridge.config.AppSecurityProperties;
import com.ghostbridge.constants.GhostBridgeAppLiterals;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Locale;

/** Throttling keys of one login attempt: the user name (bounded, lower-cased) and the client address, kept apart. */
final class LoginKeys {
    private LoginKeys() {
    }

    static String user(HttpServletRequest request) {
        String name = request.getParameter(GhostBridgeAppLiterals.LOGIN_PARAM_USERNAME);
        String bounded = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        // Scoped to the client address: a stranger cannot lock a known account (for example the published demo user) for everyone.
        return GhostBridgeAppLiterals.LOGIN_KEY_USER_PREFIX + (bounded.length() > GhostBridgeAppLiterals.MAX_USERNAME_LENGTH
                ? bounded.substring(0, GhostBridgeAppLiterals.MAX_USERNAME_LENGTH) : bounded) + "@" + request.getRemoteAddr();
    }

    static String address(HttpServletRequest request) {
        return GhostBridgeAppLiterals.LOGIN_KEY_ADDRESS_PREFIX + request.getRemoteAddr();
    }

    static List<String> of(HttpServletRequest request) {
        return List.of(user(request), address(request));
    }

    /** The failure count at which a key is blocked: the stricter one for a user name, the looser one for an address. */
    static int threshold(String key, AppSecurityProperties.LoginAttempts settings) {
        return key.startsWith(GhostBridgeAppLiterals.LOGIN_KEY_ADDRESS_PREFIX) ? settings.maxFailuresPerAddress() : settings.maxFailures();
    }
}
