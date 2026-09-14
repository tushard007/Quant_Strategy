package org.factor_investing.quant_strategy.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final DatabaseUserDetailsService users;

    public DatabaseJwtAuthenticationConverter(DatabaseUserDetailsService users) { this.users = users; }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AccountPrincipal account;
        try {
            account = users.loadUserByUsername(jwt.getSubject());
        } catch (UsernameNotFoundException exception) {
            throw invalidToken();
        }
        Object version = jwt.getClaims().get("auth_version");
        if (!account.enabled() || !(version instanceof Number number)
                || number.longValue() != account.tokenVersion()) {
            throw invalidToken();
        }
        // Use current database permissions, so role/status/password changes invalidate existing access.
        return new JwtAuthenticationToken(jwt, account.getAuthorities(), account.email());
    }

    private OAuth2AuthenticationException invalidToken() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Account access has changed. Sign in again.", null));
    }
}
