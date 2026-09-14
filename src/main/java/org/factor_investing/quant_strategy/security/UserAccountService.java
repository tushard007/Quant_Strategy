package org.factor_investing.quant_strategy.security;

import jakarta.validation.Valid;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Service
@Validated
public class UserAccountService {
    private final UserAccountRepository accounts;
    private final DatabaseSigningKeyRepository keys;
    private final PasswordEncoder encoder;
    private final EntityManager entityManager;

    public UserAccountService(UserAccountRepository accounts, DatabaseSigningKeyRepository keys,
                              PasswordEncoder encoder, EntityManager entityManager) {
        this.accounts = accounts;
        this.keys = keys;
        this.encoder = encoder;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<AccountView> list(String actorEmail, long actorVersion) {
        UserAccount actor = requireAccountManager(actorEmail, actorVersion);
        var visibleAccounts = actor.getRole() == AccountRole.SUPERADMIN
                ? accounts.findAllByOrderByEmailAsc() : accounts.findAllByRoleOrderByEmailAsc(AccountRole.USER);
        return visibleAccounts.stream().map(AccountView::from).toList();
    }

    @Transactional
    public AccountView create(String actorEmail, long actorVersion, @Valid CreateAccount request) {
        lockAdministration();
        UserAccount actor = requireAccountManager(actorEmail, actorVersion);
        requireManageableRole(actor, request.role());
        return AccountView.from(insert(request));
    }

    @Transactional
    public AccountView update(String actorEmail, long actorVersion, long id, @Valid UpdateAccount request) {
        lockAdministration();
        UserAccount actor = requireAccountManager(actorEmail, actorVersion);
        UserAccount account = accounts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Account not found"));
        entityManager.refresh(account);
        requireManageableRole(actor, account.getRole());
        requireManageableRole(actor, request.role());
        if (account.getVersion() != request.version()) {
            throw new ResponseStatusException(CONFLICT, "This account was changed. Refresh the list and try again.");
        }
        if (account.getRole() == AccountRole.SUPERADMIN && account.isEnabled()
                && (request.role() != AccountRole.SUPERADMIN || !request.enabled())
                && accounts.countByRoleAndEnabledTrue(AccountRole.SUPERADMIN) <= 1) {
            throw new ResponseStatusException(CONFLICT, "At least one enabled Superadmin must remain.");
        }
        String hash = request.password() == null || request.password().isEmpty()
                ? null : hashPassword(request.password());
        account.updateAccess(request.role(), request.enabled(), hash);
        return AccountView.from(accounts.saveAndFlush(account));
    }

    // Bootstrap adds the first Superadmin to new or existing installations; it never overwrites an account.
    @Transactional
    public boolean bootstrap(@Valid CreateAccount request) {
        lockAdministration();
        if (accounts.existsByRole(AccountRole.SUPERADMIN)) return false;
        if (request.role() != AccountRole.SUPERADMIN) throw new IllegalArgumentException("Bootstrap account must be a Superadmin");
        insert(request);
        return true;
    }

    private UserAccount insert(CreateAccount request) {
        if (accounts.existsByEmail(request.email())) {
            throw new ResponseStatusException(CONFLICT, "An account with this email already exists.");
        }
        return accounts.saveAndFlush(new UserAccount(request.email(), hashPassword(request.password()), request.role()));
    }

    private void lockAdministration() {
        if (keys.lockAccountAdministration() == null) {
            throw new IllegalStateException("Authentication storage has not been initialized");
        }
    }

    private UserAccount requireAccountManager(String email, long version) {
        UserAccount actor = accounts.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "Administrator access required"));
        // Recheck fresh state after waiting for the administration lock, even with an existing persistence context.
        entityManager.refresh(actor);
        if (!actor.isEnabled() || (actor.getRole() != AccountRole.ADMIN && actor.getRole() != AccountRole.SUPERADMIN) || actor.getTokenVersion() != version) {
            throw new ResponseStatusException(FORBIDDEN, "Administrator access has changed. Sign in again.");
        }
        return actor;
    }

    private void requireManageableRole(UserAccount actor, AccountRole targetRole) {
        if (actor.getRole() != AccountRole.SUPERADMIN && targetRole != AccountRole.USER) {
            throw new ResponseStatusException(FORBIDDEN, "Only a Superadmin can manage administrator accounts or assign elevated roles.");
        }
    }

    private String hashPassword(String password) {
        if (password == null || password.isBlank() || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(BAD_REQUEST, "Passwords must be at least 12 characters and at most 72 UTF-8 bytes.");
        }
        return encoder.encode(password);
    }

    public record CreateAccount(@NotBlank @Email @Size(max = 254) String email,
                                @NotBlank @Size(min = 12, max = 72) String password,
                                @NotNull AccountRole role) {
        public CreateAccount { email = UserAccount.normalizeEmail(email); }
        @Override public String toString() { return "CreateAccount[email=" + email + ", role=" + role + "]"; }
    }

    public record UpdateAccount(@NotNull AccountRole role, boolean enabled,
                                @Size(max = 72) String password, @Min(0) long version) {
        @Override public String toString() { return "UpdateAccount[role=" + role + ", enabled=" + enabled + "]"; }
    }

    public record AccountView(Long id, String email, AccountRole role, boolean enabled, Instant createdAt, long version) {
        static AccountView from(UserAccount account) {
            return new AccountView(account.getId(), account.getEmail(), account.getRole(), account.isEnabled(),
                    account.getCreatedAt(), account.getVersion());
        }
    }
}
