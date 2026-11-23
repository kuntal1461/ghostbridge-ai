package com.ghostbridge.security;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The only source of requester identity for the AI API: the Spring Security {@link Authentication}. Nothing from the
 * request body, parameters or headers is ever consulted. The identity is mapped to a fixed-format SHA-256 key so it has
 * a bounded length and charset, cannot collide through formatting tricks, and keeps user names out of metadata.
 */
public class AiRequesterResolver {
    private final String requiredAuthority;

    public AiRequesterResolver(String requiredRole) {
        this.requiredAuthority = GhostBridgeAiLiterals.ROLE_AUTHORITY_PREFIX + requiredRole;
    }

    /**
     * @return the stable requester key of the authenticated, authorized caller
     * @throws AuthenticationCredentialsNotFoundException (→ 401) when nobody, or only an anonymous principal, is authenticated
     * @throws AccessDeniedException                      (→ 403) when the caller lacks the required role
     */
    public String requireRequester() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(authentication)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        if (authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).noneMatch(requiredAuthority::equals)) {
            throw new AccessDeniedException("Access is denied");
        }
        return keyOf(authentication.getName());
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getName() != null && !authentication.getName().isBlank();
    }

    private static String keyOf(String name) {
        try {
            MessageDigest digest = MessageDigest.getInstance(GhostBridgeAiLiterals.REQUESTER_KEY_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(name.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required digest algorithm is unavailable", e);
        }
    }
}
