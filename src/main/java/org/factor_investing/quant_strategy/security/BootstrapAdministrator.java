package org.factor_investing.quant_strategy.security;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BootstrapAdministrator implements ApplicationRunner {
    private final UserAccountService accounts;
    private final String email;
    private final String password;

    public BootstrapAdministrator(UserAccountService accounts,
            @Value("${app.auth.bootstrap-superadmin-email:}") String email,
            @Value("${app.auth.bootstrap-superadmin-password:}") String password) {
        this.accounts = accounts;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("First-Superadmin bootstrap requires both email and password");
        }
        boolean created = accounts.bootstrap(new UserAccountService.CreateAccount(email, password, AccountRole.SUPERADMIN));
        LoggerFactory.getLogger(getClass()).info(created
                ? "First Superadmin saved to database. Remove the bootstrap credentials from server configuration."
                : "Bootstrap skipped: a Superadmin account already exists.");
    }
}
