package com.ghostbridge.restcontroller;

import com.ghostbridge.config.ConditionalOnAiWebSecurity;
import com.ghostbridge.constants.GhostBridgeAppLiterals;
import com.ghostbridge.responsevo.AuthCsrfResponseVO;
import com.ghostbridge.responsevo.AuthMeResponseVO;
import com.ghostbridge.security.AuthResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session helpers for the browser. {@code /csrf} is public (it starts the session and hands out the CSRF token that login
 * itself requires); {@code /me} needs an authenticated session. Login and logout are handled by the security filters.
 */
@RestController
@ConditionalOnAiWebSecurity   // the endpoints need the security context and the CSRF token that only the enabled chain provides
@RequestMapping(GhostBridgeAppLiterals.AUTH_BASE_PATH)
public class AuthRestController {

    @GetMapping(GhostBridgeAppLiterals.AUTH_PATH_CSRF)
    public ResponseEntity<AuthCsrfResponseVO> csrf(CsrfToken token, Authentication authentication, HttpServletRequest request) {
        // The injected token is lazy: reading it is what creates the session, so it must be read before the session is looked up.
        AuthCsrfResponseVO body = new AuthCsrfResponseVO(token.getHeaderName(), token.getToken());
        HttpSession session = request.getSession(false);
        if (session != null && (authentication == null || authentication instanceof AnonymousAuthenticationToken)) {
            session.setMaxInactiveInterval(GhostBridgeAppLiterals.ANONYMOUS_SESSION_SECONDS); // signed-out sessions expire quickly
        }
        return noStore(body);
    }

    @GetMapping(GhostBridgeAppLiterals.AUTH_PATH_ME)
    public ResponseEntity<AuthMeResponseVO> me(Authentication authentication) {
        return noStore(AuthResponses.me(authentication));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
