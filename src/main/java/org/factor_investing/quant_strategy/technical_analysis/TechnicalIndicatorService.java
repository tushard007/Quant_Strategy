package org.factor_investing.quant_strategy.technical_analysis;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.supertrend.SuperTrendIndicator;
import org.ta4j.core.num.Num;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class TechnicalIndicatorService {

    private final StockPriceCacheService stockPriceCacheService;

    private final BarSeriesService barSeriesService;

    public TechnicalIndicatorService(StockPriceCacheService stockPriceCacheService, BarSeriesService barSeriesService) {
        this.stockPriceCacheService = stockPriceCacheService;
        this.barSeriesService = barSeriesService;
    }

    /**
     * Calculates the latest EMA value for each symbol from the cached OHLCV data.
     * Returns a map of symbol -> latest EMA (ta4j Num). Logs and skips symbols with no data.
     */
    public Map<String, List<Double>> calculateLatestEma(int barCount, AssetDataType assetDataType) {
        Map<String, List<OHLCV>> stockData = getPriceData(assetDataType);
        Map<String, List<Double>> emaResults = new TreeMap<>();

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
                        if (percentageDiff.doubleValue() >= 5) {
                            emaResults.put(symbol, Arrays.asList(lastClose.doubleValue(), emaValue.doubleValue(), percentageDiff.doubleValue()));
                        }
                        log.info("EMA({}) for {} = {}, lastClose = {}, diff = {}%", barCount, symbol, emaValue, lastClose, percentageDiff);
                    } else {
                        log.debug("Skipping {}: NaN values detected", symbol);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to compute EMA for {}: {}", symbol, e.getMessage(), e);
            }
        });
        return emaResults;
    }

    /**
     * Calculates the latest Supertrend value for each symbol from cached OHLCV data.
     */
    public Map<String, SuperTrendResult> calculateLatestSuperTrend(int barCount, double multiplier, AssetDataType assetDataType) {
        return calculateLatestSuperTrend(barCount, multiplier, assetDataType, PriceFrequencey.DAILY);
    }

    /**
     * Calculates the latest Supertrend value for each symbol from cached OHLCV data.
     */
    public Map<String, SuperTrendResult> calculateLatestSuperTrend(int barCount, double multiplier, AssetDataType assetDataType, PriceFrequencey priceFrequencey) {
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be greater than zero");
        }
        if (multiplier <= 0) {
            throw new IllegalArgumentException("multiplier must be greater than zero");
        }

        Map<String, List<OHLCV>> stockData = getPriceData(assetDataType);
        Map<String, SuperTrendResult> superTrendResults = new TreeMap<>();

        stockData.forEach((symbol, ohlcvList) -> {
            try {
                if (ohlcvList == null || ohlcvList.isEmpty()) {
                    log.debug("Skipping {}: no OHLCV data", symbol);
                    return;
                }

                BarSeries series = PriceFrequencey.WEEKLY == priceFrequencey
                        ? barSeriesService.buildWeeklySeriesFromStockPrice(ohlcvList)
                        : barSeriesService.buildSeriesFromStockPrice(ohlcvList);
                if (series == null || series.getBarCount() == 0) {
                    log.debug("Skipping {}: built empty series", symbol);
                    return;
                }

                SuperTrendIndicator superTrend = new SuperTrendIndicator(series, barCount, multiplier);

                int lastIndex = series.getEndIndex();
                if (lastIndex >= 0) {
                    Num lastClose = series.getBar(lastIndex).getClosePrice();
                    Num superTrendValue = superTrend.getValue(lastIndex);
                    Num upperBand = superTrend.getSuperTrendUpperBandIndicator().getValue(lastIndex);
                    Num lowerBand = superTrend.getSuperTrendLowerBandIndicator().getValue(lastIndex);

                    if (!superTrendValue.isNaN() && !lastClose.isNaN()) {
                        Num difference = lastClose.minus(superTrendValue);
                        Num percentageDiff = difference.dividedBy(superTrendValue).multipliedBy(series.numFactory().hundred());
                        String trend = lastClose.isGreaterThan(superTrendValue) ? "BULLISH" : "BEARISH";

                        superTrendResults.put(symbol, new SuperTrendResult(
                                lastClose.doubleValue(),
                                superTrendValue.doubleValue(),
                                percentageDiff.doubleValue(),
                                upperBand.doubleValue(),
                                lowerBand.doubleValue(),
                                trend
                        ));
                        log.info("SuperTrend({}, {}, {}) for {} = {}, lastClose = {}, diff = {}%, trend = {}",
                                barCount, multiplier, priceFrequencey, symbol, superTrendValue, lastClose, percentageDiff, trend);
                    } else {
                        log.debug("Skipping {}: NaN values detected", symbol);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to compute Supertrend for {}: {}", symbol, e.getMessage(), e);
            }
        });
        return superTrendResults;
    }

    private Map<String, List<OHLCV>> getPriceData(AssetDataType assetDataType) {
        if (AssetDataType.STOCK == assetDataType) {
            return stockPriceCacheService.getCachedAllStockPriceData();
        }
        if (AssetDataType.ETF == assetDataType) {
            return stockPriceCacheService.getCachedAllIndexPriceData();
        }
        return new HashMap<>();
    }

    /**
     * Generate Excel file from emaResult list
     */
    public byte[] generateExcel(Map<String, List<Double>> emaResults, int barCount) {

        ByteArrayOutputStream outputStream;
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Technical Analysis");

            // Create header style ONCE (outside loop)
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setItalic(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);

            // Borders
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Center alignment (optional)
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] columns = {"Symbol", "Closing Price", "EMA Value(" + barCount + " days)", "% difference"};

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            AtomicInteger rowNum = new AtomicInteger(1);
            emaResults.forEach((symbol, values) -> {
                Row row = sheet.createRow(rowNum.getAndIncrement());
                row.createCell(0).setCellValue(symbol);
                row.createCell(1).setCellValue(values.get(0));
                row.createCell(2).setCellValue(values.get(1));
                row.createCell(3).setCellValue(values.get(2));
            });

            // Auto-size columns
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.setAutoFilter(new CellRangeAddress(0, rowNum.get() - 1, 0, columns.length - 1));

            // Write to byte array
            outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputStream.toByteArray();
    }

    /**
     * Generate Excel file from Supertrend results.
     */
    public byte[] generateSuperTrendExcel(Map<String, SuperTrendResult> superTrendResults, int barCount, double multiplier) {

        ByteArrayOutputStream outputStream;
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Supertrend Analysis");

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setItalic(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);

            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            Row headerRow = sheet.createRow(0);
            String[] columns = {
                    "Symbol",
                    "Closing Price",
                    "Supertrend Value(" + barCount + " days, " + multiplier + " multiplier)",
                    "% difference",
                    "Upper Band",
                    "Lower Band",
                    "Trend"
            };

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            AtomicInteger rowNum = new AtomicInteger(1);
            superTrendResults.forEach((symbol, result) -> {
                Row row = sheet.createRow(rowNum.getAndIncrement());
                row.createCell(0).setCellValue(symbol);
                row.createCell(1).setCellValue(result.getClosingPrice());
                row.createCell(2).setCellValue(result.getSuperTrendValue());
                row.createCell(3).setCellValue(result.getPercentageDifference());
                row.createCell(4).setCellValue(result.getUpperBand());
                row.createCell(5).setCellValue(result.getLowerBand());
                row.createCell(6).setCellValue(result.getTrend());
            });

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.setAutoFilter(new CellRangeAddress(0, rowNum.get() - 1, 0, columns.length - 1));

            outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputStream.toByteArray();
    }

}
