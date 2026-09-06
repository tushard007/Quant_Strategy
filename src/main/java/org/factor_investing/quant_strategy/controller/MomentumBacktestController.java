package org.factor_investing.quant_strategy.controller;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.factor_investing.quant_strategy.model.response.MomentumBacktestResult;
import org.factor_investing.quant_strategy.model.response.MomentumBacktestExecutionSummary;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.service.NiftyIndexStockService;
import org.factor_investing.quant_strategy.strategies.momentum.MomentumBacktestHistoryService;
import org.factor_investing.quant_strategy.strategies.momentum.MomentumBacktestService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/momentum-backtest")
public class MomentumBacktestController {
    private final MomentumBacktestService service;
    private final MomentumBacktestHistoryService historyService;
    private final NiftyIndexStockService niftyIndexStockService;
    public MomentumBacktestController(MomentumBacktestService service,MomentumBacktestHistoryService historyService,NiftyIndexStockService niftyIndexStockService){this.service=service;this.historyService=historyService;this.niftyIndexStockService=niftyIndexStockService;}

    @PostMapping("/run/stock")
    public ResponseEntity<MomentumBacktestResult> run(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
      @RequestParam(defaultValue="1000000") double initialCapital,@RequestParam(defaultValue="10") int entryRank,
      @RequestParam(defaultValue="20") int retentionRank,@RequestParam(defaultValue="NIFTY 500") String benchmark,
      @RequestParam(defaultValue="NIFTY50") NiftyIndexName niftyIndex,
      @RequestParam(defaultValue="0.25") double transactionCostPercent,@RequestParam(defaultValue="0.1") double slippagePercent,
      @RequestParam(defaultValue="6") double riskFreeRatePercent,
      @RequestParam(defaultValue="REPLACEMENT_ONLY") String rebalanceMode,
      @RequestParam(defaultValue="0") double bufferAmount,@RequestParam(defaultValue="0") double maximumLeverageAmount,
      @RequestParam(defaultValue="0") double borrowingInterestRatePercent){
        MomentumBacktestResult result=service.run(startDate,endDate,initialCapital,entryRank,retentionRank,benchmark,transactionCostPercent,slippagePercent,riskFreeRatePercent,rebalanceMode,bufferAmount,maximumLeverageAmount,borrowingInterestRatePercent,niftyIndexStockService.symbolsForIndex(niftyIndex));
        UUID runId=historyService.save(result,entryRank,retentionRank,transactionCostPercent,slippagePercent,bufferAmount,maximumLeverageAmount);
        return ResponseEntity.ok().header("X-Backtest-Run-Id",runId.toString()).body(result);
    }

    @GetMapping("/executions")
    public List<MomentumBacktestExecutionSummary> executions(){return historyService.history();}

    @GetMapping("/executions/{runId}")
    public MomentumBacktestResult execution(@PathVariable UUID runId){return historyService.get(runId);}

    @GetMapping("/export/{runId}")
    public ResponseEntity<byte[]> exportExecution(@PathVariable UUID runId){
        MomentumBacktestResult result=historyService.get(runId);
        return excelResponse(result);
    }

    @PostMapping("/export/stock")
    public ResponseEntity<byte[]> export(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
      @RequestParam(defaultValue="1000000") double initialCapital,@RequestParam(defaultValue="10") int entryRank,
      @RequestParam(defaultValue="20") int retentionRank,@RequestParam(defaultValue="NIFTY 500") String benchmark,
      @RequestParam(defaultValue="NIFTY50") NiftyIndexName niftyIndex,
      @RequestParam(defaultValue="0.25") double transactionCostPercent,@RequestParam(defaultValue="0.1") double slippagePercent,
      @RequestParam(defaultValue="6") double riskFreeRatePercent,
      @RequestParam(defaultValue="REPLACEMENT_ONLY") String rebalanceMode,
      @RequestParam(defaultValue="0") double bufferAmount,@RequestParam(defaultValue="0") double maximumLeverageAmount,
      @RequestParam(defaultValue="0") double borrowingInterestRatePercent){
        MomentumBacktestResult result=service.run(startDate,endDate,initialCapital,entryRank,retentionRank,benchmark,transactionCostPercent,slippagePercent,riskFreeRatePercent,rebalanceMode,bufferAmount,maximumLeverageAmount,borrowingInterestRatePercent,niftyIndexStockService.symbolsForIndex(niftyIndex));
        historyService.save(result,entryRank,retentionRank,transactionCostPercent,slippagePercent,bufferAmount,maximumLeverageAmount);
        return excelResponse(result);
    }
    private ResponseEntity<byte[]> excelResponse(MomentumBacktestResult result){
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=momentum-backtest-"+result.startDate()+"-to-"+result.endDate()+".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(workbook(result));
    }
    private byte[] workbook(MomentumBacktestResult result){try(Workbook workbook=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
        CellStyle header=workbook.createCellStyle();Font font=workbook.createFont();font.setBold(true);header.setFont(font);
        Sheet summary=workbook.createSheet("Summary");String[][] metrics={{"Metric","Value"},{"Start",result.startDate().toString()},{"End",result.endDate().toString()},{"Rebalance mode",result.rebalanceMode()},{"Final value",String.valueOf(result.finalValue())},{"Total return",String.valueOf(result.totalReturn())},{"CAGR",String.valueOf(result.cagr())},{"Max drawdown",String.valueOf(result.maximumDrawdown())},{"Benchmark",result.benchmark()},{"Benchmark return",String.valueOf(result.benchmarkReturn())},{"Benchmark CAGR",String.valueOf(result.benchmarkCagr())},{"Benchmark max drawdown",String.valueOf(result.benchmarkMaximumDrawdown())},{"Excess return",String.valueOf(result.excessReturn())},{"Annualized volatility",String.valueOf(result.annualizedVolatility())},{"Risk-free rate %",String.valueOf(result.riskFreeRatePercent())},{"Sharpe ratio",String.valueOf(result.sharpeRatio())},{"Sortino ratio",String.valueOf(result.sortinoRatio())},{"Calmar ratio",String.valueOf(result.calmarRatio())},{"Monthly win rate %",String.valueOf(result.monthlyWinRate())},{"Benchmark outperformance rate %",String.valueOf(result.benchmarkOutperformanceRate())},{"Total costs",String.valueOf(result.totalCosts())}};for(int i=0;i<metrics.length;i++){Row row=summary.createRow(i);row.createCell(0).setCellValue(metrics[i][0]);row.createCell(1).setCellValue(metrics[i][1]);}summary.getRow(0).forEach(c->c.setCellStyle(header));
        Sheet equity=workbook.createSheet("Portfolio vs Benchmark");head(equity,header,"Date","Portfolio Value","Benchmark Value","Drawdown");for(int i=0;i<result.equityCurve().size();i++){var p=result.equityCurve().get(i);Row r=equity.createRow(i+1);r.createCell(0).setCellValue(p.date().toString());r.createCell(1).setCellValue(p.portfolioValue());r.createCell(2).setCellValue(p.benchmarkValue());r.createCell(3).setCellValue(p.drawdown());}
        Sheet rebalances=workbook.createSheet("Rebalances");head(rebalances,header,"Signal Date","Execution Date","Portfolio Value","Benchmark Value","Cash","Turnover %","Costs","Retained","Sold","Bought");for(int i=0;i<result.rebalances().size();i++){var b=result.rebalances().get(i);Row r=rebalances.createRow(i+1);r.createCell(0).setCellValue(b.signalDate().toString());r.createCell(1).setCellValue(b.executionDate().toString());r.createCell(2).setCellValue(b.portfolioValue());r.createCell(3).setCellValue(b.benchmarkValue());r.createCell(4).setCellValue(b.cash());r.createCell(5).setCellValue(b.turnoverPercent());r.createCell(6).setCellValue(b.costs());r.createCell(7).setCellValue(b.decisions().stream().filter(d->d.action().equals("KEEP")).count());r.createCell(8).setCellValue(b.decisions().stream().filter(d->d.action().equals("SELL")).count());r.createCell(9).setCellValue(b.decisions().stream().filter(d->d.action().equals("BUY")).count());}
        Sheet ranks=workbook.createSheet("Rank Decisions");head(ranks,header,"Signal Date","Execution Date","Ticker","Action","Previous Rank","Current Rank","12M Rank","6M Rank","3M Rank","Total Rank Score","Original Entry Date","Execution Price","Quantity","Realized P/L");int row=1;for(var b:result.rebalances())for(var d:b.decisions()){Row r=ranks.createRow(row++);r.createCell(0).setCellValue(b.signalDate().toString());r.createCell(1).setCellValue(b.executionDate().toString());r.createCell(2).setCellValue(d.ticker());r.createCell(3).setCellValue(d.action());r.createCell(4).setCellValue(d.previousRank()==null?0:d.previousRank());r.createCell(5).setCellValue(d.currentRank());r.createCell(6).setCellValue(d.rank12());r.createCell(7).setCellValue(d.rank6());r.createCell(8).setCellValue(d.rank3());r.createCell(9).setCellValue(d.totalRank());r.createCell(10).setCellValue(d.originalEntryDate()==null?"":d.originalEntryDate().toString());r.createCell(11).setCellValue(d.executionPrice());r.createCell(12).setCellValue(d.quantity());r.createCell(13).setCellValue(d.realizedProfitLoss()==null?0:d.realizedProfitLoss());}
        Sheet yearly=workbook.createSheet("Yearly Performance");head(yearly,header,"Year","Portfolio Return","Benchmark Return","Excess Return","Max Drawdown","Turnover %","Costs","Monthly Win Rate %");row=1;for(var item:result.yearlyPerformance()){Row r=yearly.createRow(row++);r.createCell(0).setCellValue(item.year());r.createCell(1).setCellValue(item.portfolioReturn());r.createCell(2).setCellValue(item.benchmarkReturn());r.createCell(3).setCellValue(item.excessReturn());r.createCell(4).setCellValue(item.maximumDrawdown());r.createCell(5).setCellValue(item.turnoverPercent());r.createCell(6).setCellValue(item.costs());r.createCell(7).setCellValue(item.monthlyWinRate());}
        Sheet rolling=workbook.createSheet("Rolling 12M");head(rolling,header,"Window End","Months","Portfolio Return","Benchmark Return","Excess Return","Max Drawdown","Annualized Volatility");row=1;for(var item:result.rollingPerformance()){Row r=rolling.createRow(row++);r.createCell(0).setCellValue(item.endDate().toString());r.createCell(1).setCellValue(item.months());r.createCell(2).setCellValue(item.portfolioReturn());r.createCell(3).setCellValue(item.benchmarkReturn());r.createCell(4).setCellValue(item.excessReturn());r.createCell(5).setCellValue(item.maximumDrawdown());r.createCell(6).setCellValue(item.annualizedVolatility());}
        Sheet winners=workbook.createSheet("Winner Contribution");head(winners,header,"Ticker","Realized P/L","Unrealized P/L","Total Contribution","% of Net Portfolio Profit");row=1;for(var item:result.winnerContributions()){Row r=winners.createRow(row++);r.createCell(0).setCellValue(item.ticker());r.createCell(1).setCellValue(item.realizedProfitLoss());r.createCell(2).setCellValue(item.unrealizedProfitLoss());r.createCell(3).setCellValue(item.totalContribution());r.createCell(4).setCellValue(item.contributionPercentOfNetProfit());}
        Sheet stability=workbook.createSheet("Parameter Stability");head(stability,header,"Entry Rank","Retention Rank","Total Return","CAGR","Max Drawdown","Sharpe Ratio","Turnover %","Total Costs");row=1;for(var item:result.parameterStability()){Row r=stability.createRow(row++);r.createCell(0).setCellValue(item.entryRank());r.createCell(1).setCellValue(item.retentionRank());r.createCell(2).setCellValue(item.totalReturn());r.createCell(3).setCellValue(item.cagr());r.createCell(4).setCellValue(item.maximumDrawdown());r.createCell(5).setCellValue(item.sharpeRatio());r.createCell(6).setCellValue(item.turnoverPercent());r.createCell(7).setCellValue(item.totalCosts());}
        Sheet walkForward=workbook.createSheet("Walk Forward");head(walkForward,header,"Training Start","Training End","Test Start","Test End","Selected Entry Rank","Selected Retention Rank","Training Sharpe","Test Return","Test CAGR","Test Max Drawdown","Test Sharpe","Benchmark Test Return","Test Excess Return");row=1;for(var item:result.walkForwardWindows()){Row r=walkForward.createRow(row++);r.createCell(0).setCellValue(item.trainingStart().toString());r.createCell(1).setCellValue(item.trainingEnd().toString());r.createCell(2).setCellValue(item.testStart().toString());r.createCell(3).setCellValue(item.testEnd().toString());r.createCell(4).setCellValue(item.selectedEntryRank());r.createCell(5).setCellValue(item.selectedRetentionRank());r.createCell(6).setCellValue(item.trainingSharpeRatio());r.createCell(7).setCellValue(item.testReturn());r.createCell(8).setCellValue(item.testCagr());r.createCell(9).setCellValue(item.testMaximumDrawdown());r.createCell(10).setCellValue(item.testSharpeRatio());r.createCell(11).setCellValue(item.benchmarkTestReturn());r.createCell(12).setCellValue(item.testExcessReturn());}
        workbook.write(out);return out.toByteArray();}catch(Exception e){throw new IllegalStateException("Momentum backtest Excel export failed",e);}}
    private void head(Sheet sheet,CellStyle style,String... names){Row row=sheet.createRow(0);for(int i=0;i<names.length;i++){Cell c=row.createCell(i);c.setCellValue(names[i]);c.setCellStyle(style);sheet.setColumnWidth(i,4800);}}
}
