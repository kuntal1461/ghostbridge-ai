package com.ghostbridge.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Masks credentials and tokens before text is logged. Use for any untrusted or structured text. */
public final class SensitiveDataMasker {
    private static final String MASK = "****";
    private static final Set<String> SENSITIVE_HEADERS =
            Set.of("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key");

    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization[\"']?\\s*[:=]\\s*[\"']?)(?:(?:bearer|basic|digest)\\s+)?[^\\s,;&}\"']+");
    private static final Pattern KEYED_SECRET = Pattern.compile(
            "(?i)((?:password|passwd|pwd|secret|token|access_token|refresh_token|api[_-]?key|credentials?)[\"']?\\s*[:=]\\s*[\"']?)[^\\s,;&}\"']+");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bbearer\\s+[a-z0-9._~+/=-]+");

    private SensitiveDataMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = AUTHORIZATION.matcher(text).replaceAll("$1" + MASK);
        masked = KEYED_SECRET.matcher(masked).replaceAll("$1" + MASK);
        return BEARER.matcher(masked).replaceAll("Bearer " + MASK);
    }

    public static String maskHeader(String name, String value) {
        if (name != null && SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            return MASK;
        }
        return value;
    }
}
