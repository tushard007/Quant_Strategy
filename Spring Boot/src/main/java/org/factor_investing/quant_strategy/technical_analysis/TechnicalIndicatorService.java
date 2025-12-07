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

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TechnicalIndicatorService {
    @Autowired
    private StockPriceCacheService stockPriceCacheService;

    @Autowired
    private BarSeriesService barSeriesService;

    public void emaIndicator(int barCount) {
        Map<String, List<OHLCV>> stockData = stockPriceCacheService.getCachedAllStockPriceData();
        stockData.forEach((symbol, ohlcvList) -> {
            BarSeries series = barSeriesService.buildSeriesFromStockPrice(ohlcvList);

            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

            EMAIndicator ema50 = new EMAIndicator(closePrice, barCount);

            int lastIndex = series.getEndIndex();
            Num emaValue = ema50.getValue(lastIndex);

            log.info("Series for stock: {} built successfully", symbol);
            log.info("ema ({}): {}", barCount, emaValue);
        });
    }
}
