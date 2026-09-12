package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.BreadthScoreConfiguration;
import org.factor_investing.quant_strategy.model.request.BreadthScoreConfigurationRequest;
import org.factor_investing.quant_strategy.model.response.BreadthScoreConfigurationResponse;
import org.factor_investing.quant_strategy.repository.BreadthScoreConfigurationRepository;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthScoreParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Service
public class BreadthScoreConfigurationService {
    private final BreadthScoreConfigurationRepository configurationRepository;

    public BreadthScoreConfigurationService(BreadthScoreConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    @Transactional
    public BreadthScoreConfigurationResponse active() {
        BreadthScoreConfiguration configuration = configurationRepository.findByActiveTrue()
                .orElseGet(this::persistDefaults);
        return BreadthScoreConfigurationResponse.from(configuration);
    }

    @Transactional
    public BreadthScoreConfigurationResponse createVersion(BreadthScoreConfigurationRequest request) {
        validateComponentNames(request);
        BreadthScoreParameters validated = new BreadthScoreParameters(1, request.weights(),
                request.greenThreshold(), request.amberThreshold(), request.vixSpikePercent(),
                request.risingFallingLookbackSessions(), request.expansionContractionLookbackSessions());
        configurationRepository.findByActiveTrue().ifPresent(configuration -> {
            configuration.setActive(false);
            configurationRepository.saveAndFlush(configuration);
        });
        int nextVersion = configurationRepository.findFirstByOrderByVersionDesc()
                .map(configuration -> configuration.getVersion() + 1).orElse(1);
        BreadthScoreConfiguration configuration = entity(nextVersion, validated,
                request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom());
        return BreadthScoreConfigurationResponse.from(configurationRepository.save(configuration));
    }

    private void validateComponentNames(BreadthScoreConfigurationRequest request) {
        Set<String> expected = BreadthScoreParameters.defaults().weights().keySet();
        if (!new HashSet<>(request.weights().keySet()).equals(expected)) {
            throw new IllegalArgumentException("Score weights must contain exactly: " + expected);
        }
    }

    private BreadthScoreConfiguration persistDefaults() {
        BreadthScoreParameters defaults = BreadthScoreParameters.defaults();
        return configurationRepository.save(entity(defaults.version(), defaults, LocalDate.now()));
    }

    private BreadthScoreConfiguration entity(int version, BreadthScoreParameters parameters, LocalDate effectiveFrom) {
        BreadthScoreConfiguration configuration = new BreadthScoreConfiguration();
        configuration.setVersion(version);
        configuration.setWeights(parameters.weights());
        configuration.setGreenThreshold(parameters.greenThreshold());
        configuration.setAmberThreshold(parameters.amberThreshold());
        configuration.setVixSpikePercent(parameters.vixSpikePercent());
        configuration.setRisingFallingLookbackSessions(parameters.trendLookback());
        configuration.setExpansionContractionLookbackSessions(parameters.expansionLookback());
        configuration.setEffectiveFrom(effectiveFrom);
        configuration.setActive(true);
        return configuration;
    }
}
