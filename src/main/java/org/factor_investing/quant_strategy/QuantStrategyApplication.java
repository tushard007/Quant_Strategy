package org.factor_investing.quant_strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class QuantStrategyApplication {

    public static void main(String[] args) {

        SpringApplication.run(QuantStrategyApplication.class, args);
       log.info("=========Application Started===============");
    }

}
