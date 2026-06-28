package org.factor_investing.quant_strategy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "stock-price.startup-update.enabled=false")
class QuantStrategyApplicationTests {

    @Test
    void contextLoads() {
    }

}
