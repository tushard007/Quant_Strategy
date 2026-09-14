package org.factor_investing.quant_strategy.security;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserAccountRepository repository;

    public DatabaseUserDetailsService(UserAccountRepository repository) { this.repository = repository; }

    @Override
    @Transactional(readOnly = true)
    public AccountPrincipal loadUserByUsername(String email) {
        return repository.findByEmail(UserAccount.normalizeEmail(email)).map(AccountPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
    }
}
