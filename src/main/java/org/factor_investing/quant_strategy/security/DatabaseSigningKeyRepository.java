package org.factor_investing.quant_strategy.security;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DatabaseSigningKeyRepository extends JpaRepository<DatabaseSigningKey, String> {
    @Modifying
    @Query(value = "INSERT INTO app_signing_key (key_id, secret_base64) VALUES ('jwt-hs256', :secret) ON CONFLICT DO NOTHING", nativeQuery = true)
    void initializeIfAbsent(@Param("secret") String secret);

    // A shared database lock serializes account administration across application instances.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select signingKey from DatabaseSigningKey signingKey where signingKey.keyId = 'jwt-hs256'")
    DatabaseSigningKey lockAccountAdministration();
}
