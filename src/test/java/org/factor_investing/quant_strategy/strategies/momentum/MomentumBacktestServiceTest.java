package org.factor_investing.quant_strategy.strategies.momentum;

import org.factor_investing.quant_strategy.model.response.MomentumBacktestResult;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MomentumBacktestServiceTest {
    @Test
    void runsTopTenTopTwentyMonthlyStrategyAgainstBenchmark() {
        Map<String,List<OHLCV>> stocks=new LinkedHashMap<>();
        for(int i=1;i<=24;i++) stocks.put("S"+i,bars(100+i,i*.025));
        Map<String,List<OHLCV>> indexes=Map.of("NIFTY500",bars(1000,.15),
                "NIFTY500_SHARIAH",barsFrom(LocalDate.of(2025,12,1),500,.05),
                "NIFTY200",bars(900,.12),
                "NIFTY200_QUALITY_30",barsFrom(LocalDate.of(2025,12,1),450,.04));
        StockPriceCacheService cache=new StockPriceCacheService(null){
            @Override public Map<String,List<OHLCV>> getCachedAllStockPriceData(){return stocks;}
            @Override public Map<String,List<OHLCV>> getCachedAllIndexPriceData(){return indexes;}
        };

        MomentumBacktestResult result=new MomentumBacktestService(cache).run(LocalDate.of(2025,1,1),
                LocalDate.of(2026,8,1),1_000_000,10,20,"NIFTY 500",.1,.1,6.5,"REPLACEMENT_ONLY");

        assertThat(result.rebalanceCount()).isPositive();
        assertThat(result.finalValue()).isPositive();
        assertThat(result.benchmarkFinalValue()).isPositive();
        assertThat(result.annualizedVolatility()).isNotNegative();
        assertThat(result.monthlyWinRate()).isBetween(0.0,100.0);
        assertThat(result.benchmarkOutperformanceRate()).isBetween(0.0,100.0);
        assertThat(result.riskFreeRatePercent()).isEqualTo(6.5);
        assertThat(result.equityCurve()).extracting(MomentumBacktestResult.EquityPoint::date).doesNotHaveDuplicates();
        assertThat(result.equityCurve().stream().map(MomentumBacktestResult.EquityPoint::benchmarkValue).distinct().count()).isGreaterThan(1);
        assertThat(result.rebalances().getFirst().executionDate()).isAfter(result.rebalances().getFirst().signalDate());
        assertThat(result.rebalances().getFirst().decisions()).filteredOn(d->d.action().equals("BUY")).hasSize(10);
        assertThat(result.rebalances()).flatExtracting(MomentumBacktestResult.Rebalance::decisions)
                .allSatisfy(decision->assertThat(decision.quantity()).isNotNegative());
        assertThat(result.finalPositions()).hasSizeLessThanOrEqualTo(10)
                .allSatisfy(position->assertThat(position.quantity()).isPositive());

        MomentumBacktestResult nifty200Result=new MomentumBacktestService(cache).run(LocalDate.of(2025,1,1),
                LocalDate.of(2026,8,1),1_000_000,10,20,"NIFTY 200",.1,.1,6.5,"REPLACEMENT_ONLY");
        assertThat(nifty200Result.benchmarkFinalValue()).isGreaterThan(1_000_000);
        assertThat(nifty200Result.equityCurve()).extracting(MomentumBacktestResult.EquityPoint::date).doesNotHaveDuplicates();

        MomentumBacktestResult equalWeightResult=new MomentumBacktestService(cache).run(LocalDate.of(2025,1,1),
                LocalDate.of(2026,8,1),1_000_000,10,20,"NIFTY 500",.1,.1,6.5,"EQUAL_WEIGHT");
        assertThat(equalWeightResult.rebalanceMode()).isEqualTo("EQUAL_WEIGHT");
        assertThat(equalWeightResult.rebalances()).flatExtracting(MomentumBacktestResult.Rebalance::decisions)
                .anySatisfy(decision->assertThat(decision.action()).startsWith("RESIZE_"));
    }

    @Test
    void rejectsBenchmarkThatStartsAfterRequestedBacktestInsteadOfForwardLooking() {
        Map<String,List<OHLCV>> stocks=new LinkedHashMap<>();
        for(int i=1;i<=12;i++) stocks.put("S"+i,bars(100+i,i*.025));
        StockPriceCacheService cache=new StockPriceCacheService(null){
            @Override public Map<String,List<OHLCV>> getCachedAllStockPriceData(){return stocks;}
            @Override public Map<String,List<OHLCV>> getCachedAllIndexPriceData(){return Map.of("NIFTY500",barsFrom(LocalDate.of(2025,12,1),1000,.15));}
        };

        assertThatThrownBy(()->new MomentumBacktestService(cache).run(LocalDate.of(2025,1,1),
                LocalDate.of(2026,8,1),1_000_000,10,20,"NIFTY 500",.1,.1,6.5,"REPLACEMENT_ONLY"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not cover the backtest start date");
    }

    @Test
    void skipsAnEntryWhoseNextTradingBarFallsAfterTheFollowingSignal() {
        Map<String,List<OHLCV>> stocks=new LinkedHashMap<>();
        for(int i=1;i<=10;i++) stocks.put("S"+i,bars(100+i,i*.025));
        List<OHLCV> gapped=new ArrayList<>();
        LocalDate start=LocalDate.of(2024,1,1);
        for(int i=0;i<300;i++){double close=100+i*5;gapped.add(new OHLCV(Date.valueOf(start.plusDays(i)),close,close+1,close-1,close,10000+i));}
        gapped.add(new OHLCV(Date.valueOf(LocalDate.of(2026,12,1)),2000,2001,1999,2000,20000));
        stocks.put("GAP",gapped);
        StockPriceCacheService cache=new StockPriceCacheService(null){
            @Override public Map<String,List<OHLCV>> getCachedAllStockPriceData(){return stocks;}
            @Override public Map<String,List<OHLCV>> getCachedAllIndexPriceData(){return Map.of("NIFTY500",bars(1000,.15));}
        };

        MomentumBacktestResult result=new MomentumBacktestService(cache).run(LocalDate.of(2025,1,1),
                LocalDate.of(2025,5,31),1_000_000,10,20,"NIFTY 500",.1,.1,6.5,"REPLACEMENT_ONLY");

        assertThat(result.rebalances()).flatExtracting(MomentumBacktestResult.Rebalance::decisions)
                .filteredOn(decision->decision.action().equals("BUY"))
                .noneSatisfy(decision->assertThat(decision.ticker()).isEqualTo("GAP"));
    }

    @Test
    void excludesStockWithNegativeTwelveMonthMomentumDespitePositiveSixAndThreeMonthMomentum() {
        Map<String,List<OHLCV>> stocks=new LinkedHashMap<>();
        stocks.put("POSITIVE_12M",bars(100,.25));
        stocks.put("NEGATIVE_12M",negativeTwelveMonthRecoveryBars());
        StockPriceCacheService cache=new StockPriceCacheService(null){
            @Override public Map<String,List<OHLCV>> getCachedAllStockPriceData(){return stocks;}
            @Override public Map<String,List<OHLCV>> getCachedAllIndexPriceData(){return Map.of("NIFTY500",bars(1000,.15));}
        };

        MomentumBacktestResult result=new MomentumBacktestService(cache).run(LocalDate.of(2025,1,1),
                LocalDate.of(2025,1,31),1_000_000,10,20,"NIFTY 500",.1,.1,6.5,"REPLACEMENT_ONLY");

        assertThat(result.rebalances()).flatExtracting(MomentumBacktestResult.Rebalance::decisions)
                .filteredOn(decision->decision.action().equals("BUY"))
                .extracting(MomentumBacktestResult.Decision::ticker)
                .contains("POSITIVE_12M")
                .doesNotContain("NEGATIVE_12M");
    }

    private List<OHLCV> bars(double start,double step){List<OHLCV> result=new ArrayList<>();LocalDate date=LocalDate.of(2024,1,1);for(int i=0;i<1000;i++){double close=start+i*step;result.add(new OHLCV(Date.valueOf(date.plusDays(i)),close,close+1,close-1,close,10000+i));}return result;}
    private List<OHLCV> barsFrom(LocalDate date,double start,double step){List<OHLCV> result=new ArrayList<>();for(int i=0;i<500;i++){double close=start+i*step;result.add(new OHLCV(Date.valueOf(date.plusDays(i)),close,close+1,close-1,close,10000+i));}return result;}
    private List<OHLCV> negativeTwelveMonthRecoveryBars(){
        List<OHLCV> result=new ArrayList<>();LocalDate date=LocalDate.of(2024,1,1);
        for(int i=0;i<400;i++){
            double close=i<240?200-i*(100.0/240):100+(i-240)*.25;
            result.add(new OHLCV(Date.valueOf(date.plusDays(i)),close,close+1,close-1,close,10000+i));
        }
        return result;
    }
}
