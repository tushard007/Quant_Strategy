package org.factor_investing.quant_strategy.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record AccountPrincipal(String email, String passwordHash, AccountRole role,
                               boolean enabled, long tokenVersion) implements UserDetails {
    static AccountPrincipal from(UserAccount account) {
        return new AccountPrincipal(account.getEmail(), account.getPasswordHash(), account.getRole(),
                account.isEnabled(), account.getTokenVersion());
    }

    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String toString() { return "AccountPrincipal[email=" + email + ", role=" + role + "]"; }
}
