package org.factor_investing.quant_strategy.strategies.momentum;

import org.factor_investing.quant_strategy.model.response.MomentumRiskOverlayResult;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class MomentumRiskOverlayBacktestServiceTest {
    @Test void runsSeparateOverlayAndProducesAuditOutputs(){
        Map<String,List<OHLCV>> stocks=new LinkedHashMap<>();
        for(int i=1;i<=24;i++)stocks.put("S"+i,bars(100+i,i*.025,false));
        stocks.put("CRASH",bars(100,1.2,true));
        Map<String,List<OHLCV>> indexes=Map.of("NIFTY500",bars(1000,.15,false));
        StockPriceCacheService cache=new StockPriceCacheService(null){@Override public Map<String,List<OHLCV>>getCachedAllStockPriceData(){return stocks;}@Override public Map<String,List<OHLCV>>getCachedAllIndexPriceData(){return indexes;}};
        MomentumBacktestService baseline=new MomentumBacktestService(cache);
        MomentumRiskOverlayResult result=new MomentumRiskOverlayBacktestService(baseline,cache).run(
                LocalDate.of(2025,1,1),LocalDate.of(2026,8,1),1_000_000,10,20,"NIFTY 500",
                .1,.1,6.5,"REPLACEMENT_ONLY","FIXED","DAILY",20,20,3,4,1,200,40,70);
        assertThat(result.overlayFinalValue()).isPositive();
        assertThat(result.equityCurve()).isNotEmpty();
        assertThat(result.regimeHistory()).isNotEmpty();
        assertThat(result.parameterStability()).hasSize(12);
    }
    private List<OHLCV>bars(double start,double step,boolean crash){List<OHLCV>r=new ArrayList<>();LocalDate d=LocalDate.of(2024,1,1);for(int i=0;i<1000;i++){double close=start+i*step;if(crash&&i>600)close*=.55;double open=close;r.add(new OHLCV(Date.valueOf(d.plusDays(i)),open,close+1,close-1,close,10000+i));}return r;}
}
