package org.factor_investing.quant_strategy.security;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByRole(AccountRole role);
    long countByRoleAndEnabledTrue(AccountRole role);
    List<UserAccount> findAllByOrderByEmailAsc();
    List<UserAccount> findAllByRoleOrderByEmailAsc(AccountRole role);
}
