package org.factor_investing.quant_strategy.util;

import lombok.extern.slf4j.Slf4j;

import java.text.DecimalFormat;

@Slf4j
public class ReturnCalculationUtils {

    public static float percentReturn(Float startingPrice, Float closingPrice) {
        try {
            if (startingPrice == null || closingPrice == null) {
                throw new IllegalArgumentException("Starting price and closing price must not be null");
            }

            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(2);
            // Remove commas
            String closingPriceStr = closingPrice.toString().replace(",", "");
            String startingPriceStr = startingPrice.toString().replace(",", "");

            // Parse to float
            closingPrice = Float.parseFloat(closingPriceStr);
            startingPrice = Float.parseFloat(startingPriceStr);

            // Calculate price return
            float priceReturn = ((closingPrice - startingPrice) / startingPrice) * 100;
            return Float.parseFloat(df.format(priceReturn));
        } catch (NumberFormatException e) {
            log.error(STR."Error formatting the price return: \{e.getMessage()}");
            return 0.0f;
        } catch (IllegalArgumentException e) {
            log.error(STR."Invalid input: \{e.getMessage()}");
            return 0.0f;
        } catch (Exception e) {
            log.error(STR."An unexpected error occurred: \{e.getMessage()}");
            return 0.0f;
        }
    }
}