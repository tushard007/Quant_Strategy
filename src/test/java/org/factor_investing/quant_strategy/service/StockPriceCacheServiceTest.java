package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.event.PriceDataChangedEvent;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StockPriceCacheServiceTest {
    @Test
    void refreshesOnlyTheCacheForTheChangedAssetType() {
        AtomicReference<AssetDataType> refreshed = new AtomicReference<>();
        StockPriceCacheService service = new StockPriceCacheService(null) {
            @Override public Map<String, List<OHLCV>> refreshStockPriceDataCache() { refreshed.set(AssetDataType.STOCK); return Map.of(); }
            @Override public Map<String, List<OHLCV>> refreshETFPriceDataCache() { refreshed.set(AssetDataType.ETF); return Map.of(); }
            @Override public Map<String, List<OHLCV>> refreshIndexPriceDataCache() { refreshed.set(AssetDataType.INDEX); return Map.of(); }
        };

        service.refreshChangedPriceCache(new PriceDataChangedEvent(AssetDataType.ETF));

        assertThat(refreshed.get()).isEqualTo(AssetDataType.ETF);
    }
}
