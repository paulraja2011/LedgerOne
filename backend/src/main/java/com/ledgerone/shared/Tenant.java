package com.ledgerone.shared;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/** Resolves the current company (tenant) and user from the validated JWT. */
public final class Tenant {
    private Tenant() {}

    public static UUID companyId() {
        return UUID.fromString(token().getToken().getClaimAsString("cid"));
    }

    public static UUID userId() {
        return UUID.fromString(token().getToken().getSubject());
    }

    private static JwtAuthenticationToken token() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken t) {
            return t;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Not signed in");
    }
}
