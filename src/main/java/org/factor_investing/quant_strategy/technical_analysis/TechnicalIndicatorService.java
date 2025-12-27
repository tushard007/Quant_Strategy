package org.factor_investing.quant_strategy.technical_analysis;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TechnicalIndicatorService {
    @Autowired
    private StockPriceCacheService stockPriceCacheService;

    @Autowired
    private BarSeriesService barSeriesService;

    /**
     * Calculates the latest EMA value for each symbol from the cached OHLCV data.
     * Returns a map of symbol -> latest EMA (ta4j Num). Logs and skips symbols with no data.
     */
    public String calculateLatestEma(int barCount) {
        Map<String, List<OHLCV>> stockData = stockPriceCacheService.getCachedAllStockPriceData();
        Map<String, List<Double>> emaResults = new HashMap<>();

        stockData.forEach((symbol, ohlcvList) -> {
            try {
                if (ohlcvList == null || ohlcvList.isEmpty()) {
                    log.debug("Skipping {}: no OHLCV data", symbol);
                    return;
                }

                BarSeries series = barSeriesService.buildSeriesFromStockPrice(ohlcvList);
                if (series == null || series.getBarCount() == 0) {
                    log.debug("Skipping {}: built empty series", symbol);
                    return;
                }

                ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
                EMAIndicator ema = new EMAIndicator(closePrice, barCount);

                int lastIndex = series.getEndIndex();
                if (lastIndex >= 0) {
                    Num lastClose = series.getBar(lastIndex).getClosePrice();
                    Num emaValue = ema.getValue(lastIndex);

                    if (!emaValue.isNaN() && !lastClose.isNaN()) {

                        Num difference = lastClose.minus(emaValue);
                        Num percentageDiff = difference.dividedBy(emaValue).multipliedBy(series.numFactory().hundred());

                        emaResults.put(symbol, Arrays.asList(lastClose.doubleValue(), emaValue.doubleValue(), percentageDiff.doubleValue()));
                        log.info("EMA({}) for {} = {}, lastClose = {}, diff = {}%", barCount, symbol, emaValue, lastClose, percentageDiff);
                    } else {
                        log.debug("Skipping {}: NaN values detected", symbol);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to compute EMA for {}: {}", symbol, e.getMessage(), e);
            }
        });
        return STR."EMA result calculated successfully for symbols:\{emaResults.size()}";
    }
}
