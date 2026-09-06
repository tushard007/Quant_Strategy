package org.factor_investing.quant_strategy.controller;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.factor_investing.quant_strategy.model.response.MomentumRiskOverlayResult;
import org.factor_investing.quant_strategy.strategies.momentum.MomentumRiskOverlayBacktestService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/momentum-risk-overlay-backtest")
public class MomentumRiskOverlayBacktestController {
    private final MomentumRiskOverlayBacktestService service;
    public MomentumRiskOverlayBacktestController(MomentumRiskOverlayBacktestService service){this.service=service;}

    @PostMapping("/run/stock") public MomentumRiskOverlayResult run(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue="1000000") double initialCapital,@RequestParam(defaultValue="10") int entryRank,
            @RequestParam(defaultValue="20") int retentionRank,@RequestParam(defaultValue="NIFTY 500") String benchmark,
            @RequestParam(defaultValue="0.1") double transactionCostPercent,@RequestParam(defaultValue="0.1") double slippagePercent,
            @RequestParam(defaultValue="6.5") double riskFreeRatePercent,@RequestParam(defaultValue="REPLACEMENT_ONLY") String rebalanceMode,
            @RequestParam(defaultValue="FIXED") String stopModel,@RequestParam(defaultValue="DAILY") String evaluationFrequency,
            @RequestParam(defaultValue="20") double trailingStopPercent,@RequestParam(defaultValue="20") int atrPeriod,
            @RequestParam(defaultValue="3") double atrMultiplier,@RequestParam(defaultValue="4") int cooldownWeeks,
            @RequestParam(defaultValue="1") int confirmationMonths,@RequestParam(defaultValue="200") int benchmarkSmaPeriod,
            @RequestParam(defaultValue="40") double breadthThresholdPercent,@RequestParam(defaultValue="70") double weakExposureCapPercent){
        return service.run(startDate,endDate,initialCapital,entryRank,retentionRank,benchmark,transactionCostPercent,
                slippagePercent,riskFreeRatePercent,rebalanceMode,stopModel,evaluationFrequency,trailingStopPercent,
                atrPeriod,atrMultiplier,cooldownWeeks,confirmationMonths,benchmarkSmaPeriod,breadthThresholdPercent,weakExposureCapPercent);
    }

    @PostMapping("/export/stock") public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue="1000000") double initialCapital,@RequestParam(defaultValue="10") int entryRank,
            @RequestParam(defaultValue="20") int retentionRank,@RequestParam(defaultValue="NIFTY 500") String benchmark,
            @RequestParam(defaultValue="0.1") double transactionCostPercent,@RequestParam(defaultValue="0.1") double slippagePercent,
            @RequestParam(defaultValue="6.5") double riskFreeRatePercent,@RequestParam(defaultValue="REPLACEMENT_ONLY") String rebalanceMode,
            @RequestParam(defaultValue="FIXED") String stopModel,@RequestParam(defaultValue="DAILY") String evaluationFrequency,
            @RequestParam(defaultValue="20") double trailingStopPercent,@RequestParam(defaultValue="20") int atrPeriod,
            @RequestParam(defaultValue="3") double atrMultiplier,@RequestParam(defaultValue="4") int cooldownWeeks,
            @RequestParam(defaultValue="1") int confirmationMonths,@RequestParam(defaultValue="200") int benchmarkSmaPeriod,
            @RequestParam(defaultValue="40") double breadthThresholdPercent,@RequestParam(defaultValue="70") double weakExposureCapPercent){
        MomentumRiskOverlayResult result=service.run(startDate,endDate,initialCapital,entryRank,retentionRank,benchmark,
                transactionCostPercent,slippagePercent,riskFreeRatePercent,rebalanceMode,stopModel,evaluationFrequency,
                trailingStopPercent,atrPeriod,atrMultiplier,cooldownWeeks,confirmationMonths,benchmarkSmaPeriod,
                breadthThresholdPercent,weakExposureCapPercent);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=momentum-risk-overlay-"+startDate+"-to-"+endDate+".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(workbook(result));
    }
    private byte[] workbook(MomentumRiskOverlayResult x){try(Workbook w=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){CellStyle h=w.createCellStyle();Font f=w.createFont();f.setBold(true);h.setFont(f);
        Sheet s=w.createSheet("Baseline vs Overlay");head(s,h,"Metric","Baseline","Overlay");String[][] rows={{"Final value",n(x.baselineFinalValue()),n(x.overlayFinalValue())},{"CAGR",n(x.baselineCagr()),n(x.overlayCagr())},{"Maximum drawdown",n(x.baselineMaximumDrawdown()),n(x.overlayMaximumDrawdown())},{"Sharpe","",n(x.overlaySharpeRatio())},{"Total costs","",n(x.overlayTotalCosts())},{"Average exposure %","",n(x.averageExposurePercent())},{"Stop exits","",String.valueOf(x.stopExitCount())}};int i=1;for(String[]v:rows){Row r=s.createRow(i++);for(int c=0;c<v.length;c++)r.createCell(c).setCellValue(v[c]);}
        Sheet e=w.createSheet("Daily Equity");head(e,h,"Date","Portfolio","Benchmark","Drawdown","Exposure %");i=1;for(var p:x.equityCurve()){Row r=e.createRow(i++);r.createCell(0).setCellValue(p.date().toString());r.createCell(1).setCellValue(p.portfolioValue());r.createCell(2).setCellValue(p.benchmarkValue());r.createCell(3).setCellValue(p.drawdown());r.createCell(4).setCellValue(p.exposurePercent());}
        Sheet st=w.createSheet("Stop Events");head(st,h,"Ticker","Model","Entry Date","Entry Price","Highest Close","Stop Level","Breach Date","Breach Close","Execution Date","Execution Price","Quantity","Realized P/L","Re-entry Eligible");i=1;for(var p:x.stopEvents()){Row r=st.createRow(i++);r.createCell(0).setCellValue(p.ticker());r.createCell(1).setCellValue(p.stopModel());r.createCell(2).setCellValue(p.entryDate().toString());r.createCell(3).setCellValue(p.entryPrice());r.createCell(4).setCellValue(p.highestClose());r.createCell(5).setCellValue(p.stopLevel());r.createCell(6).setCellValue(p.breachDate().toString());r.createCell(7).setCellValue(p.breachClose());r.createCell(8).setCellValue(p.executionDate().toString());r.createCell(9).setCellValue(p.executionPrice());r.createCell(10).setCellValue(p.quantity());r.createCell(11).setCellValue(p.realizedProfitLoss());r.createCell(12).setCellValue(p.reentryEligibleDate().toString());}
        Sheet rg=w.createSheet("Market Regime");head(rg,h,"Signal Date","Breadth %","Benchmark Above SMA","Exposure Cap %","New Buys Allowed");i=1;for(var p:x.regimeHistory()){Row r=rg.createRow(i++);r.createCell(0).setCellValue(p.signalDate().toString());r.createCell(1).setCellValue(p.breadthPercent());r.createCell(2).setCellValue(p.benchmarkAboveSma());r.createCell(3).setCellValue(p.exposureCapPercent());r.createCell(4).setCellValue(p.newBuysAllowed());}
        Sheet rb=w.createSheet("Overlay Rebalances");head(rb,h,"Signal Date","Execution Date","Portfolio Value","Cash","Exposure %","Bought","Sold","Retained","Cooldown Blocked");i=1;for(var p:x.rebalances()){Row r=rb.createRow(i++);r.createCell(0).setCellValue(p.signalDate().toString());r.createCell(1).setCellValue(p.executionDate().toString());r.createCell(2).setCellValue(p.portfolioValue());r.createCell(3).setCellValue(p.cash());r.createCell(4).setCellValue(p.exposurePercent());r.createCell(5).setCellValue(p.bought());r.createCell(6).setCellValue(p.sold());r.createCell(7).setCellValue(p.retained());r.createCell(8).setCellValue(p.cooldownBlocked());}
        Sheet ps=w.createSheet("Overlay Stability");head(ps,h,"Stop Model","Frequency","Stop % or ATR Multiplier","Return","CAGR","Max Drawdown","Sharpe","Stop Exits","Costs");i=1;for(var p:x.parameterStability()){Row r=ps.createRow(i++);r.createCell(0).setCellValue(p.stopModel());r.createCell(1).setCellValue(p.frequency());r.createCell(2).setCellValue(p.stopValue());r.createCell(3).setCellValue(p.totalReturn());r.createCell(4).setCellValue(p.cagr());r.createCell(5).setCellValue(p.maximumDrawdown());r.createCell(6).setCellValue(p.sharpeRatio());r.createCell(7).setCellValue(p.stopExits());r.createCell(8).setCellValue(p.totalCosts());}
        w.write(out);return out.toByteArray();}catch(Exception ex){throw new IllegalStateException("Risk overlay Excel export failed",ex);}}
    private void head(Sheet s,CellStyle h,String...names){Row r=s.createRow(0);for(int i=0;i<names.length;i++){Cell c=r.createCell(i);c.setCellValue(names[i]);c.setCellStyle(h);s.setColumnWidth(i,5200);}}private String n(double v){return String.valueOf(v);}
}
