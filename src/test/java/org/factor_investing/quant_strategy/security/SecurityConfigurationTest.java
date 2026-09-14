package org.factor_investing.quant_strategy.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class SecurityConfigurationTest {
    @Test
    void passwordsAreHashedAndPrincipalDoesNotPrintCredentials() {
        PasswordEncoder encoder = new PasswordConfiguration().passwordEncoder();
        String password = "admin-test-password";
        String hash = encoder.encode(password);
        var account = new UserAccount("  Admin@Example.COM  ", hash, AccountRole.ADMIN);
        assertThat(account.getEmail()).isEqualTo("admin@example.com");
        assertThat(hash).isNotEqualTo(password);
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(AccountPrincipal.from(account).toString()).doesNotContain(hash, password);
        assertThat(new AuthController.LoginRequest(account.getEmail(), password).toString()).doesNotContain(password);
    }
}
