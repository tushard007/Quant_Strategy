package org.factor_investing.quant_strategy.technical_analysis;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.service.StockPriceCacheService;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
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
        Map<String, List<OHLCV>> stockData =new HashMap<>();
        if (AssetDataType.STOCK == assetDataType) {
            stockData = stockPriceCacheService.getCachedAllStockPriceData();
        }
        if (AssetDataType.ETF == assetDataType) {
            stockData = stockPriceCacheService.getCachedAllIndexPriceData();
        }
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

}
