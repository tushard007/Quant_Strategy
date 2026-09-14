package org.factor_investing.quant_strategy.security;

import jakarta.persistence.*;

@Entity
@Table(name = "app_signing_key")
public class DatabaseSigningKey {
    public static final String KEY_ID = "jwt-hs256";

    @Id
    @Column(name = "key_id", length = 32)
    private String keyId;

    @Column(name = "secret_base64", nullable = false, length = 128)
    private String secretBase64;

    protected DatabaseSigningKey() {}

    String secretBase64() { return secretBase64; }
}
