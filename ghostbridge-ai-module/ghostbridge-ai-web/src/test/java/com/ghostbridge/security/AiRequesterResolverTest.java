package com.ghostbridge.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRequesterResolverTest {
    private final AiRequesterResolver resolver = new AiRequesterResolver("AI_USER");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void login(String name, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(name, "n/a", AuthorityUtils.createAuthorityList(authorities)));
    }

    @Test
    void noAuthenticationIsRejectedAsUnauthenticated() {
        assertThatThrownBy(resolver::requireRequester).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void anonymousPrincipalIsRejectedAsUnauthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS", "ROLE_AI_USER")));
        assertThatThrownBy(resolver::requireRequester).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void unauthenticatedTokenAndBlankNameAreRejected() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", "y", "ROLE_AI_USER") {
            @Override
            public boolean isAuthenticated() {
                return false;
            }
        });
        assertThatThrownBy(resolver::requireRequester).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        login("   ", "ROLE_AI_USER");
        assertThatThrownBy(resolver::requireRequester).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void callerWithoutTheRoleIsForbiddenEvenWithSimilarAuthorities() {
        login("alice", "ROLE_OTHER", "AI_USER", "ROLE_AI_USER_X", "ai_user");
        assertThatThrownBy(resolver::requireRequester).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void keyIsAStableFixedFormatHashOfTheAuthenticatedName() {
        login("alice", "ROLE_AI_USER");
        String first = resolver.requireRequester();
        String second = resolver.requireRequester();
        assertThat(first).isEqualTo(second).matches("[0-9a-f]{64}").doesNotContain("alice");
    }

    @Test
    void differentNamesNeverShareAKey() {
        String[] names = {"alice", "Alice", "alice ", " alice", "alice\u0000", "alice@x", "a|lice", "ALICE"};
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (String name : names) {
            login(name, "ROLE_AI_USER");
            keys.add(resolver.requireRequester());
        }
        assertThat(keys).hasSize(names.length);
    }
}
