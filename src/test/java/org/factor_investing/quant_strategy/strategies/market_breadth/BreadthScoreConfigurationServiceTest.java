package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.BreadthScoreConfiguration;
import org.factor_investing.quant_strategy.model.request.BreadthScoreConfigurationRequest;
import org.factor_investing.quant_strategy.repository.BreadthScoreConfigurationRepository;
import org.factor_investing.quant_strategy.service.BreadthScoreConfigurationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BreadthScoreConfigurationServiceTest {

    @Test
    void createsANewVersionAndDeactivatesThePreviousConfiguration() {
        BreadthScoreConfiguration previous = configuration(3, true);
        List<BreadthScoreConfiguration> saved = new ArrayList<>();
        BreadthScoreConfigurationRepository repository = TestRepositoryProxy.create(
                BreadthScoreConfigurationRepository.class, (method, arguments) -> switch (method.getName()) {
                    case "findByActiveTrue", "findFirstByOrderByVersionDesc" -> Optional.of(previous);
                    case "save", "saveAndFlush" -> {
                        BreadthScoreConfiguration value = (BreadthScoreConfiguration) arguments[0];
                        saved.add(value);
                        yield value;
                    }
                    default -> null;
                });
        BreadthScoreConfigurationService service = new BreadthScoreConfigurationService(repository);
        BreadthScoreParameters defaults = BreadthScoreParameters.defaults();
        LocalDate effectiveFrom = LocalDate.of(2026, 10, 1);
        BreadthScoreConfigurationRequest request = new BreadthScoreConfigurationRequest(defaults.weights(),
                defaults.greenThreshold(), defaults.amberThreshold(), defaults.vixSpikePercent(),
                defaults.trendLookback(), defaults.expansionLookback(), effectiveFrom);

        var response = service.createVersion(request);

        assertThat(previous.isActive()).isFalse();
        assertThat(response.version()).isEqualTo(4);
        assertThat(response.effectiveFrom()).isEqualTo(effectiveFrom);
        assertThat(response.active()).isTrue();
        assertThat(saved).hasSize(2);
    }

    @Test
    void rejectsUnknownOrMissingComponentNames() {
        BreadthScoreConfigurationRepository repository = TestRepositoryProxy.create(
                BreadthScoreConfigurationRepository.class, (method, arguments) -> null);
        BreadthScoreConfigurationService service = new BreadthScoreConfigurationService(repository);
        BreadthScoreParameters defaults = BreadthScoreParameters.defaults();
        var invalidWeights = new HashMap<>(defaults.weights());
        invalidWeights.remove("AD_LINE_TREND");
        invalidWeights.put("UNKNOWN", 10);
        BreadthScoreConfigurationRequest request = new BreadthScoreConfigurationRequest(invalidWeights,
                defaults.greenThreshold(), defaults.amberThreshold(), defaults.vixSpikePercent(),
                defaults.trendLookback(), defaults.expansionLookback(), null);

        assertThatIllegalArgumentException().isThrownBy(() -> service.createVersion(request))
                .withMessageContaining("exactly");
    }

    private BreadthScoreConfiguration configuration(int version, boolean active) {
        BreadthScoreParameters defaults = BreadthScoreParameters.defaults();
        BreadthScoreConfiguration configuration = new BreadthScoreConfiguration();
        configuration.setVersion(version);
        configuration.setWeights(defaults.weights());
        configuration.setGreenThreshold(defaults.greenThreshold());
        configuration.setAmberThreshold(defaults.amberThreshold());
        configuration.setVixSpikePercent(defaults.vixSpikePercent());
        configuration.setRisingFallingLookbackSessions(defaults.trendLookback());
        configuration.setExpansionContractionLookbackSessions(defaults.expansionLookback());
        configuration.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        configuration.setActive(active);
        return configuration;
    }
}
