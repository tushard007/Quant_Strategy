package org.factor_investing.quant_strategy.security;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "app_user_account")
@org.hibernate.annotations.Check(constraints = "email = lower(trim(email))")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountRole role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UserAccount(String email, String passwordHash, AccountRole role) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public void updateAccess(AccountRole role, boolean enabled, String newPasswordHash) {
        if (this.role != role || this.enabled != enabled || newPasswordHash != null) {
            tokenVersion++;
        }
        this.role = role;
        this.enabled = enabled;
        if (newPasswordHash != null) this.passwordHash = newPasswordHash;
    }
}
