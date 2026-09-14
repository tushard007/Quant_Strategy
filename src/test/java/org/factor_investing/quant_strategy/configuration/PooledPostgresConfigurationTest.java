package org.factor_investing.quant_strategy.configuration;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;
import org.postgresql.PGProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.util.Properties;
import static org.assertj.core.api.Assertions.assertThat;

class PooledPostgresConfigurationTest {
    @Test
    void productionDriverDisablesNamedStatementsThroughHikariBinding() throws Exception {
        Properties config = new Properties();
        try (var stream = getClass().getResourceAsStream("/application-prod.properties")) {
            config.load(stream);
        }
        HikariConfig hikari = new HikariConfig();
        new Binder(new MapConfigurationPropertySource(config)).bind("spring.datasource.hikari", Bindable.ofInstance(hikari));
        Properties driver = org.postgresql.Driver.parseURL("jdbc:postgresql://localhost/test", hikari.getDataSourceProperties());
        assertThat(PGProperty.PREPARE_THRESHOLD.getInt(driver)).isZero();
    }
}
