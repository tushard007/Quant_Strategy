package org.factor_investing.quant_strategy.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SigningKeyStore {
    private final DatabaseSigningKeyRepository repository;

    public SigningKeyStore(DatabaseSigningKeyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SecretKey loadOrCreate() {
        byte[] candidate = new byte[32];
        new SecureRandom().nextBytes(candidate);
        repository.initializeIfAbsent(Base64.getEncoder().encodeToString(candidate));
        // A concurrent instance may have inserted first. Always use the committed database key.
        byte[] secret = Base64.getDecoder().decode(repository.findById(DatabaseSigningKey.KEY_ID)
                .orElseThrow(() -> new IllegalStateException("JWT signing key could not be initialized")).secretBase64());
        if (secret.length < 32) throw new IllegalStateException("Database JWT signing key must contain at least 32 bytes");
        return new SecretKeySpec(secret, "HmacSHA256");
    }
}
