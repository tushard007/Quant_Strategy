package org.factor_investing.quant_strategy.strategies.alpha;

import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class JensensAlphaService {

    /**
     * Calculates Jensen's Alpha for multiple stocks against a market benchmark.
     *
     * @param stocksData Map<StockName, List<OHLCV>> of the stocks to be analyzed.
     * @return Map<StockName, StockAlphaVO> containing the calculated metrics for each stock.
     */
  //  public Map<String, StockAlphaVO> calculateJensensAlpha(Map<String, List<OHLCV>> stocksData) {
//        // --- RECTIFIED: Calculate market returns once for efficiency ---
//        Map<String, List<OHLCV>> marketData = stocksData;
//        List<Double> marketReturns = calculateDailyReturns(stocksData.values());
//        double marketAnnualizedReturn = calculateAnnualizedReturnFromReturns(marketReturns);
//        double marketVariance = calculateVariance(marketReturns);
//
//        return stocksData.entrySet().stream()
//                .collect(Collectors.toMap(
//                        Map.Entry::getKey,
//                        entry -> calculateSingleStockAlpha(
//                                entry.getKey(),
//                                entry.getValue(),
//                                marketReturns,
//                                marketAnnualizedReturn,
//                                marketVariance
//                        )
//                ));
//    }
//
//    // ---------- Internal Calculations ----------------
//
//    private StockAlphaVO calculateSingleStockAlpha(String stockName,
//                                                   List<OHLCV> ohlcvList,
//                                                   List<Double> marketReturns,
//                                                   double marketAnnualizedReturn,
//                                                   double marketVariance) {
//        try {
//            validateInput(stockName, ohlcvList);
//            if (ohlcvList.size() != marketReturns.size() + 1) {
//                throw new IllegalArgumentException("Stock data and market data must cover the same period. Mismatch in size.");
//            }
//
//            // --- RECTIFIED: Use a single, consistent source for returns ---
//            List<Double> stockReturns = calculateDailyReturns(Collections.singleton(ohlcvList));
//
//            // --- RECTIFIED: Calculate all metrics from the daily returns list ---
//            double stockAnnualizedReturn = calculateAnnualizedReturnFromReturns(stockReturns);
//            double stockAnnualizedVolatility = calculateAnnualizedVolatility(stockReturns);
//            double beta = calculateBeta(stockReturns, marketReturns, marketVariance);
//
//            // --- RECTIFIED: Use the calculated actual market return ---
//            double alpha = stockAnnualizedReturn - (AlphaConstants.RISK_FREE_RATE +
//                    beta * (marketAnnualizedReturn - AlphaConstants.RISK_FREE_RATE));
//
//            // Sharpe Ratio
//            double sharpeRatio = (stockAnnualizedReturn - AlphaConstants.RISK_FREE_RATE) /
//                    (stockAnnualizedVolatility == 0 ? 1 : stockAnnualizedVolatility);
//
//            return new StockAlphaVO(stockName,
//                    alpha * 100,
//                    stockAnnualizedReturn * 100,
//                    beta,
//                    ohlcvList.size(),
//                    true,
//                    "Calculation successful",
//                    stockAnnualizedVolatility * 100,
//                    sharpeRatio);
//
//        } catch (Exception e) {
//            return new StockAlphaVO(stockName, 0.0, 0.0, 0.0,
//                    ohlcvList != null ? ohlcvList.size() : 0,
//                    false, e.getMessage(),
//                    0.0, 0.0);
//        }
//    }
//
//    // ----------------- Helper Methods -------------------
//
//    private void validateInput(String stockName, List<OHLCV> ohlcvList) {
//        if (stockName == null || stockName.trim().isEmpty()) {
//            throw new IllegalArgumentException("Stock name cannot be null or empty");
//        }
//        if (ohlcvList == null || ohlcvList.isEmpty()) {
//            throw new IllegalArgumentException("OHLCV data cannot be null or empty");
//        }
//        if (ohlcvList.size() < AlphaConstants.MIN_DATA_POINTS) {
//            throw new IllegalArgumentException("Insufficient data points. Required: " +
//                    AlphaConstants.MIN_DATA_POINTS + ", Provided: " + ohlcvList.size());
//        }
//        for (int i = 0; i < ohlcvList.size(); i++) {
//            OHLCV data = ohlcvList.get(i);
//            if (data == null || data.getClose() <= 0) {
//                throw new IllegalArgumentException("Invalid OHLCV data at index: " + i);
//            }
//        }
//    }
//
//    private List<Double> calculateDailyReturns(Collection<List<OHLCV>> ohlcvList) {
//        List<Double> returns = new ArrayList<>();
//        for (int i = 1; i < ohlcvList.size(); i++) {
//            double prevClose = ohlcvList.get(i - 1).getClose();
//            double currentClose = ohlcvList.get(i).getClose();
//            // prevClose check is good practice
//            returns.add((currentClose - prevClose) / prevClose);
//        }
//        if (returns.isEmpty()) {
//            throw new RuntimeException("Unable to calculate daily returns from provided data. Need at least 2 data points.");
//        }
//        return returns;
//    }
//
//    /**
//     * --- RECTIFIED: This is now the primary method for calculating annualized return ---
//     * Calculates the annualized return from a series of daily returns.
//     */
//    private double calculateAnnualizedReturnFromReturns(List<Double> dailyReturns) {
//        // Geometric average return
//        double totalReturn = dailyReturns.stream()
//                .reduce(1.0, (acc, ret) -> acc * (1 + ret));
//        int periods = dailyReturns.size();
//        return Math.pow(totalReturn, (double) AlphaConstants.TRADING_DAYS_PER_YEAR / periods) - 1;
//    }
//
//    private double calculateAnnualizedVolatility(List<Double> dailyReturns) {
//        double variance = calculateVariance(dailyReturns);
//        return Math.sqrt(variance) * Math.sqrt(AlphaConstants.TRADING_DAYS_PER_YEAR);
//    }
//
//    /**
//     * --- RECTIFIED: Correct Beta calculation using Covariance and Market Variance ---
//     */
//    private double calculateBeta(List<Double> stockReturns, List<Double> marketReturns, double marketVariance) {
//        if (marketVariance == 0) {
//            // Market has no volatility, beta is undefined. Can return 1.0 or throw exception.
//            return 1.0;
//        }
//        double covariance = calculateCovariance(stockReturns, marketReturns);
//        double beta = covariance / marketVariance;
//
//        // Clamping beta to a reasonable range is an acceptable practice.
//        return Math.max(AlphaConstants.MIN_BETA, Math.min(beta, AlphaConstants.MAX_BETA));
//    }
//
//    /**
//     * --- NEW: Helper method to calculate Covariance between two return series ---
//     */
//    private double calculateCovariance(List<Double> returns1, List<Double> returns2) {
//        if (returns1.size() != returns2.size() || returns1.isEmpty()) {
//            return 0.0;
//        }
//        double mean1 = returns1.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
//        double mean2 = returns2.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
//        double covariance = 0.0;
//        for (int i = 0; i < returns1.size(); i++) {
//            covariance += (returns1.get(i) - mean1) * (returns2.get(i) - mean2);
//        }
//        // Using n-1 for sample covariance, which is standard
//        return covariance / (returns1.size() - 1);
//    }
//
//    private double calculateVariance(List<Double> returns) {
//        if (returns.size() < 2) return 0.0;
//        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
//        double variance = returns.stream()
//                .mapToDouble(r -> Math.pow(r - mean, 2))
//                .sum();
//        // Using n-1 for sample variance
//        return variance / (returns.size() - 1);
//    }
//
//    /*
//     * --- REMOVED: These methods are redundant or flawed and have been replaced ---
//     * private double calculateAnnualizedReturnFromPrice(...)
//     * private double calculateAnnualizedReturn(...)
//     */
}