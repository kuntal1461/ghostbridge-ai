package com.ghostbridge.security;

import com.ghostbridge.constants.GhostBridgeAppLiterals;
import com.ghostbridge.responsevo.AuthMeResponseVO;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

/** Maps an authenticated principal to the response contract without leaking anything but name and role names. */
public final class AuthResponses {
    private AuthResponses() {
    }

    public static AuthMeResponseVO me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith(GhostBridgeAppLiterals.ROLE_PREFIX)
                        ? authority.substring(GhostBridgeAppLiterals.ROLE_PREFIX.length()) : authority)
                .sorted()
                .toList();
        return new AuthMeResponseVO(authentication.getName(), roles);
    }
}
