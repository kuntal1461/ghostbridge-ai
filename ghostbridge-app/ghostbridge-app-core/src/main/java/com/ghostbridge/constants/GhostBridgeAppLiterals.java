package com.ghostbridge.constants;

/** Shared constants of the GhostBridge App project (one literals class per project, see rules.md section 3). */
public final class GhostBridgeAppLiterals {
    public static final String APP_PROPERTY_PREFIX = "ghostbridge.app";
    public static final String APP_AI_PROPERTY_PREFIX = APP_PROPERTY_PREFIX + ".ai";
    /** Approved structured-output schema identifier → registered prompt name used when none is configured. */
    public static final String DEFAULT_STRUCTURED_SCHEMA = "generic-summary";
    public static final String DEFAULT_STRUCTURED_PROMPT = "generic-structured";
    public static final String SECURITY_PROPERTY_PREFIX = APP_PROPERTY_PREFIX + ".security";
    public static final String AUTH_BASE_PATH = "/api/v1/auth";
    public static final String AUTH_PATH_CSRF = "/csrf";
    public static final String AUTH_PATH_LOGIN = "/login";
    public static final String AUTH_PATH_LOGOUT = "/logout";
    public static final String AUTH_PATH_ME = "/me";
    public static final String ROLE_PREFIX = "ROLE_";
    public static final String PASSWORD_HASH_PREFIX = "{bcrypt}";
    public static final String LOGIN_PARAM_USERNAME = "username";
    public static final String LOGIN_PARAM_PASSWORD = "password";
    public static final int MAX_USERNAME_LENGTH = 64;
    /** Idle lifetime of a session that has not signed in (it exists only to hold the CSRF token). */
    public static final int ANONYMOUS_SESSION_SECONDS = 120;
    public static final String LOGIN_KEY_USER_PREFIX = "u:";
    public static final String LOGIN_KEY_ADDRESS_PREFIX = "a:";
    public static final String SESSION_COOKIE_NAME = "JSESSIONID";
    public static final String REGEX_USERNAME = "[A-Za-z0-9][A-Za-z0-9._@-]{0,63}";

    private GhostBridgeAppLiterals() {
    }
}
