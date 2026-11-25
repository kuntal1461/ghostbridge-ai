package com.ghostbridge.config;

import com.ghostbridge.constants.GhostBridgeAiLiterals;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates the configured Ollama destinations. Error messages name the property only and never echo the URL, because a
 * mistyped value could contain user-info credentials. Hosts are judged from the text alone: no DNS lookup happens, so a
 * host name is trusted only when it is {@code localhost} or listed explicitly by the operator.
 */
final class OllamaEndpointValidator {
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6_LITERAL = Pattern.compile("\\[[0-9a-fA-F:.]+]");
    private static final int OCTET_MAX = 255;
    private static final int PORT_MAX = 65535;
    private static final String CLOUD_URL = GhostBridgeAiLiterals.PROPERTY_PREFIX + ".ollama.cloud.base-url";
    private static final String LOCAL_URL = GhostBridgeAiLiterals.PROPERTY_PREFIX + ".ollama.local.base-url";

    private OllamaEndpointValidator() {
    }

    /** Cloud: https, allow-listed host, plain origin on the default port. */
    static void validateCloud(String baseUrl, List<String> allowedHosts) {
        URI uri = parse(baseUrl, CLOUD_URL);
        if (!GhostBridgeAiLiterals.URI_SCHEME_HTTPS.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(CLOUD_URL + " must use https");
        }
        if (uri.getHost() == null || !allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(CLOUD_URL + " host is not in ghostbridge.ai.ollama.allowed-hosts");
        }
        boolean portOk = uri.getPort() == -1 || uri.getPort() == GhostBridgeAiLiterals.HTTPS_DEFAULT_PORT;
        if (!isPlainOrigin(uri) || !portOk) {
            throw new IllegalArgumentException(CLOUD_URL
                    + " must be a plain https origin (no user-info, path, query, fragment or custom port)");
        }
    }

    /** Local: http or https plain origin on a loopback or private host, or on a host the operator allow-listed. */
    static void validateLocal(String baseUrl, List<String> allowedHosts) {
        URI uri = parse(baseUrl, LOCAL_URL);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException(LOCAL_URL + " must be an absolute http or https URL with a host");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!GhostBridgeAiLiterals.URI_SCHEME_HTTP.equals(scheme) && !GhostBridgeAiLiterals.URI_SCHEME_HTTPS.equals(scheme)) {
            throw new IllegalArgumentException(LOCAL_URL + " must use http or https");
        }
        if (!isPlainOrigin(uri) || uri.getPort() == 0 || uri.getPort() > PORT_MAX) {
            throw new IllegalArgumentException(LOCAL_URL + " must be a plain origin (no user-info, path, query or fragment)");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (isCloudHost(host)) {
            throw new IllegalArgumentException(LOCAL_URL + " must not point at Ollama Cloud");
        }
        if (!isApprovedLocalHost(host, allowedHosts)) {
            throw new IllegalArgumentException(LOCAL_URL + " host must be loopback, a private address "
                    + "or listed in ghostbridge.ai.ollama.local.allowed-hosts");
        }
    }

    private static boolean isPlainOrigin(URI uri) {
        String path = uri.getRawPath();
        boolean pathOk = path == null || path.isEmpty() || "/".equals(path);
        String authority = uri.getRawAuthority();
        boolean emptyPort = authority != null && authority.endsWith(":");
        return pathOk && !emptyPort && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null;
    }

    private static boolean isCloudHost(String host) {
        String name = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        return name.equals(GhostBridgeAiLiterals.OLLAMA_CLOUD_DOMAIN) || name.endsWith("." + GhostBridgeAiLiterals.OLLAMA_CLOUD_DOMAIN);
    }

    private static boolean isApprovedLocalHost(String host, List<String> allowedHosts) {
        if (host.isEmpty()) {
            return false;
        }
        if (GhostBridgeAiLiterals.LOCALHOST.equals(host) || allowedHosts.contains(host)) {
            return true;
        }
        byte[] address = literalAddress(host);
        return address != null && (address.length == 4 ? isPrivateIpv4(address) : isPrivateIpv6(address));
    }

    /** Returns the bytes of an IP literal, or null when the text is not a literal (never resolves names). */
    private static byte[] literalAddress(String host) {
        if (IPV4.matcher(host).matches()) {
            String[] parts = host.split("\\.");
            byte[] bytes = new byte[parts.length];
            for (int i = 0; i < parts.length; i++) {
                int octet = Integer.parseInt(parts[i]);
                boolean leadingZero = parts[i].length() > 1 && parts[i].charAt(0) == '0'; // ambiguous decimal/octal: refuse
                if (octet > OCTET_MAX || leadingZero) {
                    return null;
                }
                bytes[i] = (byte) octet;
            }
            return bytes;
        }
        if (IPV6_LITERAL.matcher(host).matches()) {
            try {
                return InetAddress.getByName(host).getAddress();
            } catch (UnknownHostException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean isPrivateIpv4(byte[] a) {
        int first = a[0] & 0xFF;
        int second = a[1] & 0xFF;
        return first == 127 || first == 10 || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }

    /** Loopback (::1) and unique local (fc00::/7). Link-local ranges (169.254/16, fe80::/10) need explicit allow-listing. */
    private static boolean isPrivateIpv6(byte[] a) {
        boolean loopback = a[15] == 1;
        for (int i = 0; i < 15 && loopback; i++) {
            loopback = a[i] == 0;
        }
        int first = a[0] & 0xFF;
        return loopback || (first & 0xFE) == 0xFC;
    }

    private static URI parse(String baseUrl, String property) {
        try {
            return new URI(baseUrl == null ? "" : baseUrl.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(property + " is not a valid URI");
        }
    }
}
