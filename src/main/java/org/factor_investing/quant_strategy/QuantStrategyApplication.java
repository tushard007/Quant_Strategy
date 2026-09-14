package org.factor_investing.quant_strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class QuantStrategyApplication {

    public static void main(String[] args) {

        var context = SpringApplication.run(QuantStrategyApplication.class, args);
        if (context.getEnvironment().getProperty("price-data.import.run", Boolean.class, false)) {
            int exitCode = 0;
            try {
                String source = context.getEnvironment().getRequiredProperty("price-data.import.source");
                var timeFrame = org.factor_investing.quant_strategy.model.PriceFrequencey.valueOf(
                        context.getEnvironment().getProperty("price-data.import.time-frame", "DAILY"));
                var result = context.getBean(org.factor_investing.quant_strategy.service.PriceUpdateJobService.class)
                        .runBlocking(source, timeFrame, context.getEnvironment().getProperty("price-data.import.run-id"));
                log.info("Price import completed: {}", result);
            } catch (Exception exception) {
                log.error("Price import failed", exception);
                exitCode = 1;
            } finally {
                context.close();
            }
            System.exit(exitCode);
        }
        log.info("=========Application Started===============");
    }

}
