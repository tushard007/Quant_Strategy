package org.factor_investing.quant_strategy.strategies.momentum;

import org.factor_investing.quant_strategy.model.response.MomentumBacktestResult;
import org.factor_investing.quant_strategy.model.response.MomentumRiskOverlayResult;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.factor_investing.quant_strategy.util.DateUtil;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MomentumRiskOverlayBacktestService {
    private final MomentumBacktestService baselineService;
    private final StockPriceCacheService cacheService;

    public MomentumRiskOverlayBacktestService(MomentumBacktestService baselineService, StockPriceCacheService cacheService) {
        this.baselineService = baselineService; this.cacheService = cacheService;
    }

    public MomentumRiskOverlayResult run(LocalDate startDate, LocalDate endDate, double initialCapital,
            int entryRank, int retentionRank, String benchmark, double transactionCostPercent,
            double slippagePercent, double riskFreeRatePercent, String rebalanceMode,
            String stopModel, String evaluationFrequency, double trailingStopPercent,
            int atrPeriod, double atrMultiplier, int cooldownWeeks, int confirmationMonths,
            int benchmarkSmaPeriod, double breadthThresholdPercent, double weakExposureCapPercent) {
        stopModel=stopModel==null?"":stopModel.trim().toUpperCase(Locale.ROOT);
        evaluationFrequency=evaluationFrequency==null?"":evaluationFrequency.trim().toUpperCase(Locale.ROOT);
        validate(stopModel, evaluationFrequency, trailingStopPercent, atrPeriod, atrMultiplier, cooldownWeeks,
                confirmationMonths, benchmarkSmaPeriod, breadthThresholdPercent, weakExposureCapPercent);
        int stabilityAtrPeriod=Math.max(2,atrPeriod);
        double stabilityAtrMultiplier=atrMultiplier>0?atrMultiplier:3;
        MomentumBacktestResult baseline = baselineService.runCore(startDate, endDate, initialCapital, entryRank,
                retentionRank, benchmark, transactionCostPercent, slippagePercent, riskFreeRatePercent,
                rebalanceMode, 0, 0, 0);
        Map<String,NavigableMap<LocalDate,OHLCV>> stocks=normalize(cacheService.getCachedAllStockPriceData());
        NavigableMap<LocalDate,OHLCV> index=resolveBenchmark(normalize(cacheService.getCachedAllIndexPriceData()),benchmark);
        Simulation selected=simulate(baseline,stocks,index,initialCapital,entryRank,transactionCostPercent,
                slippagePercent,riskFreeRatePercent,rebalanceMode,stopModel,evaluationFrequency,
                trailingStopPercent,atrPeriod,atrMultiplier,cooldownWeeks,confirmationMonths,
                benchmarkSmaPeriod,breadthThresholdPercent,weakExposureCapPercent);
        List<MomentumRiskOverlayResult.StabilityRun> grid=new ArrayList<>();
        for(String frequency:List.of("DAILY","WEEKLY")) for(double width:List.of(12d,15d,20d)) {
            Simulation run=simulate(baseline,stocks,index,initialCapital,entryRank,transactionCostPercent,
                    slippagePercent,riskFreeRatePercent,rebalanceMode,"FIXED",frequency,width,stabilityAtrPeriod,
                    stabilityAtrMultiplier,cooldownWeeks,confirmationMonths,benchmarkSmaPeriod,breadthThresholdPercent,weakExposureCapPercent);
            grid.add(run.stability("FIXED",frequency,width));
        }
        for(String frequency:List.of("DAILY","WEEKLY")) for(double multiplier:List.of(2d,3d,4d)) {
            Simulation run=simulate(baseline,stocks,index,initialCapital,entryRank,transactionCostPercent,
                    slippagePercent,riskFreeRatePercent,rebalanceMode,"ATR",frequency,trailingStopPercent,stabilityAtrPeriod,
                    multiplier,cooldownWeeks,confirmationMonths,benchmarkSmaPeriod,breadthThresholdPercent,weakExposureCapPercent);
            grid.add(run.stability("ATR",frequency,multiplier));
        }
        return new MomentumRiskOverlayResult(startDate,endDate,benchmark,stopModel,evaluationFrequency,
                baseline.finalValue(),baseline.cagr(),baseline.maximumDrawdown(),selected.finalValue,
                selected.totalReturn,selected.cagr,selected.maxDrawdown,selected.sharpe,selected.totalCosts,
                selected.averageExposure,selected.stopEvents.size(),selected.cooldownBlocked,
                selected.curve,selected.stopEvents,selected.regimes,selected.rebalances,grid);
    }

    private Simulation simulate(MomentumBacktestResult baseline,Map<String,NavigableMap<LocalDate,OHLCV>> stocks,
            NavigableMap<LocalDate,OHLCV> index,double initialCapital,int entryRank,double transactionCostPercent,
            double slippagePercent,double riskFreeRatePercent,String rebalanceMode,String stopModel,
            String frequency,double stopPercent,int atrPeriod,double atrMultiplier,int cooldownWeeks,
            int confirmationMonths,int benchmarkSmaPeriod,double breadthThreshold,double weakCap) {
        double cash=initialCapital,costs=0,peak=initialCapital; Map<String,Holding> holdings=new LinkedHashMap<>();
        Map<String,LocalDate> cooldown=new HashMap<>(); Map<String,Integer> confirmation=new HashMap<>();
        List<MomentumRiskOverlayResult.EquityPoint> curve=new ArrayList<>();
        List<MomentumRiskOverlayResult.StopEvent> stops=new ArrayList<>();
        List<MomentumRiskOverlayResult.RegimePoint> regimes=new ArrayList<>();
        List<MomentumRiskOverlayResult.OverlayRebalance> rebalances=new ArrayList<>(); int blockedTotal=0;
        double benchmarkEntry=price(index,baseline.equityCurve().getFirst().date(),true);
        curve.add(new MomentumRiskOverlayResult.EquityPoint(baseline.equityCurve().getFirst().date(),initialCapital,initialCapital,0,0));
        LocalDate lastProcessed=baseline.equityCurve().getFirst().date();
        for(MomentumBacktestResult.Rebalance base:baseline.rebalances()) {
            StopProcessing stopResult=processStops(lastProcessed,base.signalDate(),holdings,cooldown,stocks,index,cash,costs,
                    transactionCostPercent,slippagePercent,stopModel,frequency,stopPercent,atrPeriod,atrMultiplier,
                    cooldownWeeks,initialCapital,benchmarkEntry,peak,curve,stops);
            cash=stopResult.cash; costs=stopResult.costs; peak=stopResult.peak;
            List<MomentumBacktestResult.Decision> candidates=base.decisions().stream()
                    .filter(d->!"SELL".equals(d.action())).sorted(Comparator.comparingInt(MomentumBacktestResult.Decision::currentRank)).toList();
            Set<String> eligibleNow=candidates.stream().map(MomentumBacktestResult.Decision::ticker).collect(Collectors.toSet());
            confirmation.replaceAll((ticker,count)->eligibleNow.contains(ticker)?count:0);
            eligibleNow.forEach(ticker->confirmation.merge(ticker,1,Integer::sum));
            double breadth=breadth(stocks,base.signalDate(),benchmarkSmaPeriod);
            boolean indexAbove=aboveSma(index,base.signalDate(),benchmarkSmaPeriod);
            double cap=breadth<breadthThreshold?weakCap:100;
            boolean buysAllowed=indexAbove;
            regimes.add(new MomentumRiskOverlayResult.RegimePoint(base.signalDate(),breadth,indexAbove,cap,buysAllowed));
            int maxPositions=Math.max(0,(int)Math.floor(entryRank*cap/100.0));
            List<String> desired=candidates.stream().map(MomentumBacktestResult.Decision::ticker).distinct().limit(maxPositions).toList();
            int sold=0,bought=0,retained=0,blocked=0; double traded=0;
            for(String ticker:new ArrayList<>(holdings.keySet())) if(!desired.contains(ticker)) {
                Trade sale=sell(ticker,base.executionDate(),holdings,stocks,transactionCostPercent,slippagePercent);
                if(sale!=null){cash+=sale.cashFlow;costs+=sale.cost;traded+=sale.notional;sold++;}
            }
            double value=value(holdings,stocks,base.signalDate(),cash); double target="EQUAL_WEIGHT".equalsIgnoreCase(rebalanceMode)?value/Math.max(1,entryRank):initialCapital/Math.max(1,entryRank);
            for(String ticker:desired){if(holdings.containsKey(ticker)){retained++;continue;}
                LocalDate allowed=cooldown.get(ticker); if(!buysAllowed||(allowed!=null&&base.executionDate().isBefore(allowed))||confirmation.getOrDefault(ticker,0)<confirmationMonths){blocked++;continue;}
                OHLCV bar=barAtOrAfter(stocks.get(ticker),base.executionDate());if(bar==null||bar.getOpen()<=0)continue;
                double raw=bar.getOpen(),execution=raw*(1+slippagePercent/100),unit=execution*(1+transactionCostPercent/100);
                long quantity=(long)Math.floor(Math.min(target,cash)/unit);if(quantity<1)continue;
                double invested=quantity*unit;cash-=invested;double cost=quantity*(execution*transactionCostPercent/100+raw*slippagePercent/100);costs+=cost;traded+=quantity*execution;
                LocalDate entryDate=dateOf(stocks.get(ticker),bar);holdings.put(ticker,new Holding(entryDate,execution,quantity,invested,bar.getClose()));bought++;
            }
            blockedTotal+=blocked;double portfolio=value(holdings,stocks,base.signalDate(),cash);double exposure=exposure(holdings,stocks,base.signalDate(),portfolio);
            rebalances.add(new MomentumRiskOverlayResult.OverlayRebalance(base.signalDate(),base.executionDate(),portfolio,cash,exposure,bought,sold,retained,blocked));
            lastProcessed=base.signalDate();
        }
        StopProcessing finalStops=processStops(lastProcessed,baseline.endDate(),holdings,cooldown,stocks,index,cash,costs,
                transactionCostPercent,slippagePercent,stopModel,frequency,stopPercent,atrPeriod,atrMultiplier,
                cooldownWeeks,initialCapital,benchmarkEntry,peak,curve,stops);
        cash=finalStops.cash;costs=finalStops.costs;peak=finalStops.peak;
        double finalValue=value(holdings,stocks,baseline.endDate(),cash),totalReturn=finalValue/initialCapital-1;
        double years=Math.max(1.0/365.25,ChronoUnit.DAYS.between(baseline.startDate(),baseline.endDate())/365.25);
        double cagr=Math.pow(finalValue/initialCapital,1/years)-1;
        double maxDrawdown=curve.stream().mapToDouble(MomentumRiskOverlayResult.EquityPoint::drawdown).min().orElse(0);
        List<Double> returns=new ArrayList<>();for(int i=1;i<curve.size();i++){double prior=curve.get(i-1).portfolioValue();if(prior>0)returns.add(curve.get(i).portfolioValue()/prior-1);}
        double mean=returns.stream().mapToDouble(Double::doubleValue).average().orElse(0),variance=returns.size()<2?0:returns.stream().mapToDouble(r->Math.pow(r-mean,2)).sum()/(returns.size()-1);
        double volatility=Math.sqrt(variance)*Math.sqrt(252),sharpe=volatility==0?0:(cagr-riskFreeRatePercent/100)/volatility;
        double averageExposure=curve.stream().mapToDouble(MomentumRiskOverlayResult.EquityPoint::exposurePercent).average().orElse(0);
        return new Simulation(finalValue,totalReturn,cagr,maxDrawdown,sharpe,costs,averageExposure,blockedTotal,curve,stops,regimes,rebalances);
    }

    private StopProcessing processStops(LocalDate from,LocalDate to,Map<String,Holding> holdings,Map<String,LocalDate> cooldown,
            Map<String,NavigableMap<LocalDate,OHLCV>> stocks,NavigableMap<LocalDate,OHLCV> index,double cash,double costs,
            double transactionCost,double slippage,String model,String frequency,double stopPercent,int atrPeriod,
            double atrMultiplier,int cooldownWeeks,double initialCapital,double benchmarkEntry,double peak,
            List<MomentumRiskOverlayResult.EquityPoint> curve,List<MomentumRiskOverlayResult.StopEvent> events){
        if(from==null||to==null||!from.isBefore(to))return new StopProcessing(cash,costs,peak);
        Map<String,PendingStop> pending=new HashMap<>();
        for(LocalDate date:index.subMap(from,false,to,true).keySet()){
            for(String ticker:new ArrayList<>(pending.keySet())){PendingStop pendingStop=pending.get(ticker);if(date.isBefore(pendingStop.executionDate))continue;
                Holding holding=holdings.get(ticker);Trade sale=sell(ticker,pendingStop.executionDate,holdings,stocks,transactionCost,slippage);pending.remove(ticker);if(sale==null||holding==null)continue;
                cash+=sale.cashFlow;costs+=sale.cost;LocalDate eligible=pendingStop.executionDate.plusWeeks(cooldownWeeks);cooldown.put(ticker,eligible);
                events.add(new MomentumRiskOverlayResult.StopEvent(ticker,model,holding.entryDate,holding.entryPrice,pendingStop.highestClose,pendingStop.stopLevel,pendingStop.breachDate,pendingStop.breachClose,pendingStop.executionDate,sale.executionPrice,holding.quantity,sale.pnl,eligible));}
            if(checkDate(date,to,frequency))for(String ticker:new ArrayList<>(holdings.keySet())){
                if(pending.containsKey(ticker))continue;
                Holding holding=holdings.get(ticker);OHLCV bar=barAtOrBefore(stocks.get(ticker),date);if(bar==null)continue;
                holding.peak=Math.max(holding.peak,bar.getClose());double level=stopLevel(model,holding,stocks.get(ticker),date,stopPercent,atrPeriod,atrMultiplier);
                if(bar.getClose()<=level){LocalDate executionDate=stocks.get(ticker).higherKey(date);if(executionDate==null||executionDate.isAfter(to))continue;
                    pending.put(ticker,new PendingStop(executionDate,holding.peak,level,date,bar.getClose()));}
            }
            double portfolio=value(holdings,stocks,date,cash);peak=Math.max(peak,portfolio);double benchmark=initialCapital*price(index,date,false)/benchmarkEntry;
            curve.add(new MomentumRiskOverlayResult.EquityPoint(date,portfolio,benchmark,peak==0?0:portfolio/peak-1,exposure(holdings,stocks,date,portfolio)));
        }return new StopProcessing(cash,costs,peak);
    }

    private boolean checkDate(LocalDate date,LocalDate periodEnd,String frequency){return "DAILY".equals(frequency)||("WEEKLY".equals(frequency)&&date.getDayOfWeek()==DayOfWeek.FRIDAY)||("MONTHLY".equals(frequency)&&date.equals(periodEnd));}
    private double stopLevel(String model,Holding h,NavigableMap<LocalDate,OHLCV>s,LocalDate date,double fixed,int atrPeriod,double multiplier){
        if("ATR".equals(model)){double atr=atr(s,date,atrPeriod);return atr<=0?Double.NEGATIVE_INFINITY:h.peak-multiplier*atr;}
        double width=fixed;if("TIERED".equals(model)){double gain=h.peak/h.entryPrice-1;if(gain>=.6)width=Math.max(width,25);else if(gain>=.3)width=Math.max(width,20);}return h.peak*(1-width/100);
    }
    private double atr(NavigableMap<LocalDate,OHLCV>s,LocalDate date,int period){List<OHLCV> bars=new ArrayList<>(s.headMap(date,true).values());if(bars.size()<2)return 0;int from=Math.max(1,bars.size()-period);double sum=0;for(int i=from;i<bars.size();i++){OHLCV b=bars.get(i),p=bars.get(i-1);sum+=Math.max(b.getHigh()-b.getLow(),Math.max(Math.abs(b.getHigh()-p.getClose()),Math.abs(b.getLow()-p.getClose())));}return sum/(bars.size()-from);}
    private double breadth(Map<String,NavigableMap<LocalDate,OHLCV>>stocks,LocalDate date,int period){int valid=0,above=0;for(var s:stocks.values()){List<OHLCV>b=new ArrayList<>(s.headMap(date,true).values());if(b.size()<period)continue;valid++;double sma=b.subList(b.size()-period,b.size()).stream().mapToDouble(OHLCV::getClose).average().orElse(0);if(b.getLast().getClose()>sma)above++;}return valid==0?0:above*100.0/valid;}
    private boolean aboveSma(NavigableMap<LocalDate,OHLCV>s,LocalDate date,int period){List<OHLCV>b=new ArrayList<>(s.headMap(date,true).values());if(b.size()<period)return false;double sma=b.subList(b.size()-period,b.size()).stream().mapToDouble(OHLCV::getClose).average().orElse(0);return b.getLast().getClose()>sma;}
    private Trade sell(String ticker,LocalDate date,Map<String,Holding>holdings,Map<String,NavigableMap<LocalDate,OHLCV>>stocks,double transaction,double slippage){Holding h=holdings.get(ticker);OHLCV b=barAtOrAfter(stocks.get(ticker),date);if(h==null||b==null)return null;double raw=b.getOpen(),execution=raw*(1-slippage/100),proceeds=h.quantity*execution*(1-transaction/100),cost=h.quantity*(execution*transaction/100+raw*slippage/100);holdings.remove(ticker);return new Trade(proceeds,cost,h.quantity*execution,execution,proceeds-h.invested);}
    private double value(Map<String,Holding>h,Map<String,NavigableMap<LocalDate,OHLCV>>s,LocalDate d,double cash){return cash+h.entrySet().stream().mapToDouble(e->{OHLCV b=barAtOrBefore(s.get(e.getKey()),d);return e.getValue().quantity*(b==null?e.getValue().entryPrice:b.getClose());}).sum();}
    private double exposure(Map<String,Holding>h,Map<String,NavigableMap<LocalDate,OHLCV>>s,LocalDate d,double value){if(value<=0)return 0;return (value(h,s,d,0)/value)*100;}
    private double price(NavigableMap<LocalDate,OHLCV>s,LocalDate d,boolean open){OHLCV b=barAtOrBefore(s,d);if(b==null)throw new IllegalArgumentException("Benchmark price unavailable on "+d);return open?b.getOpen():b.getClose();}
    private OHLCV barAtOrBefore(NavigableMap<LocalDate,OHLCV>s,LocalDate d){var e=s==null?null:s.floorEntry(d);return e==null?null:e.getValue();}private OHLCV barAtOrAfter(NavigableMap<LocalDate,OHLCV>s,LocalDate d){var e=s==null?null:s.ceilingEntry(d);return e==null?null:e.getValue();}
    private LocalDate dateOf(NavigableMap<LocalDate,OHLCV>s,OHLCV b){return s.entrySet().stream().filter(e->e.getValue()==b).map(Map.Entry::getKey).findFirst().orElse(DateUtil.convertDateToLocalDate(b.getDate()));}
    private Map<String,NavigableMap<LocalDate,OHLCV>> normalize(Map<String,List<OHLCV>>source){Map<String,NavigableMap<LocalDate,OHLCV>>r=new HashMap<>();if(source!=null)source.forEach((t,bars)->{TreeMap<LocalDate,OHLCV>m=new TreeMap<>();if(bars!=null)bars.stream().filter(Objects::nonNull).filter(b->b.getDate()!=null).forEach(b->m.put(DateUtil.convertDateToLocalDate(b.getDate()),b));r.put(t,m);});return r;}
    private NavigableMap<LocalDate,OHLCV> resolveBenchmark(Map<String,NavigableMap<LocalDate,OHLCV>>indexes,String requested){String n=normalizeName(requested);return indexes.entrySet().stream().filter(e->normalizeName(e.getKey()).equals(n)).map(Map.Entry::getValue).findFirst().orElseThrow(()->new IllegalArgumentException("Benchmark index not found: "+requested));}
    private String normalizeName(String value){return value==null?"":value.replaceAll("[^A-Za-z0-9]","").toUpperCase();}
    private void validate(String model,String frequency,double stop,int atr,double multiplier,int cooldown,int confirmation,int sma,double breadth,double cap){
        if(!List.of("FIXED","TIERED","ATR").contains(model))throw new IllegalArgumentException("Stop model must be FIXED, TIERED or ATR");
        if(!List.of("DAILY","WEEKLY","MONTHLY").contains(frequency))throw new IllegalArgumentException("Evaluation frequency must be DAILY, WEEKLY or MONTHLY");
        if(("FIXED".equals(model)||"TIERED".equals(model))&&(stop<=0||stop>=100))throw new IllegalArgumentException("Trailing stop percent must be greater than 0 and less than 100");
        if("ATR".equals(model)&&atr<2)throw new IllegalArgumentException("ATR period must be at least 2");
        if("ATR".equals(model)&&multiplier<=0)throw new IllegalArgumentException("ATR multiplier must be greater than 0");
        if(cooldown<0)throw new IllegalArgumentException("Cooldown weeks cannot be negative");
        if(confirmation<1)throw new IllegalArgumentException("Confirmation months must be at least 1");
        if(sma<2)throw new IllegalArgumentException("Benchmark SMA period must be at least 2");
        if(breadth<0||breadth>100)throw new IllegalArgumentException("Breadth threshold must be between 0 and 100 percent");
        if(cap<0||cap>100)throw new IllegalArgumentException("Weak exposure cap must be between 0 and 100 percent");
    }
    private static class Holding{LocalDate entryDate;double entryPrice;long quantity;double invested,peak;Holding(LocalDate d,double p,long q,double i,double peak){entryDate=d;entryPrice=p;quantity=q;invested=i;this.peak=peak;}}
    private record Trade(double cashFlow,double cost,double notional,double executionPrice,double pnl){}private record StopProcessing(double cash,double costs,double peak){}private record PendingStop(LocalDate executionDate,double highestClose,double stopLevel,LocalDate breachDate,double breachClose){}
    private record Simulation(double finalValue,double totalReturn,double cagr,double maxDrawdown,double sharpe,double totalCosts,double averageExposure,int cooldownBlocked,List<MomentumRiskOverlayResult.EquityPoint>curve,List<MomentumRiskOverlayResult.StopEvent>stopEvents,List<MomentumRiskOverlayResult.RegimePoint>regimes,List<MomentumRiskOverlayResult.OverlayRebalance>rebalances){MomentumRiskOverlayResult.StabilityRun stability(String model,String frequency,double value){return new MomentumRiskOverlayResult.StabilityRun(model,frequency,value,totalReturn,cagr,maxDrawdown,sharpe,stopEvents.size(),totalCosts);}}
}
