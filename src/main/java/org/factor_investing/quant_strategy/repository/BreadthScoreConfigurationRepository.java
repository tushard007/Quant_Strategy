package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.BreadthScoreConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BreadthScoreConfigurationRepository extends JpaRepository<BreadthScoreConfiguration, UUID> {
    Optional<BreadthScoreConfiguration> findByActiveTrue();

    Optional<BreadthScoreConfiguration> findByVersion(int version);

    Optional<BreadthScoreConfiguration> findFirstByOrderByVersionDesc();
}
