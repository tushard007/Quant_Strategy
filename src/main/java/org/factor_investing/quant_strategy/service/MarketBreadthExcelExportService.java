package org.factor_investing.quant_strategy.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.factor_investing.quant_strategy.model.AlertDeliveryState;
import org.factor_investing.quant_strategy.model.BreadthScoreConfiguration;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.response.BreadthAlertResponse;
import org.factor_investing.quant_strategy.model.response.BreadthSnapshotResponse;
import org.factor_investing.quant_strategy.repository.BreadthScoreConfigurationRepository;
import org.factor_investing.quant_strategy.strategies.market_breadth.RequiredMarketBreadthIndex;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MarketBreadthExcelExportService {
    private static final int EXPORT_HISTORY_LIMIT = 2000;

    private final MarketBreadthQueryService queryService;
    private final BreadthAlertService alertService;
    private final BreadthScoreConfigurationRepository configurationRepository;

    public MarketBreadthExcelExportService(MarketBreadthQueryService queryService,
                                           BreadthAlertService alertService,
                                           BreadthScoreConfigurationRepository configurationRepository) {
        this.queryService = queryService;
        this.alertService = alertService;
        this.configurationRepository = configurationRepository;
    }

    @Transactional(readOnly = true)
    public ExportResult export(NiftyIndexName universe, LocalDate from, LocalDate to) {
        validateRange(from, to);
        List<BreadthSnapshotResponse> history = queryService.history(universe, from, to, EXPORT_HISTORY_LIMIT);
        List<BreadthAlertResponse> alerts = alertService.history(
                universe, null, null, from, to, BreadthAlertService.MAX_ALERT_LIMIT);
        Map<Integer, BreadthScoreConfiguration> configurations = history.stream()
                .map(BreadthSnapshotResponse::scoreConfigurationVersion)
                .distinct()
                .map(configurationRepository::findByVersion)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.toMap(BreadthScoreConfiguration::getVersion, Function.identity()));

        LocalDate actualFrom = history.isEmpty() ? from : history.getFirst().tradingDate();
        LocalDate actualTo = history.isEmpty() ? to : history.getLast().tradingDate();
        return new ExportResult(buildWorkbook(history, alerts, configurations), actualFrom, actualTo);
    }

    byte[] buildWorkbook(List<BreadthSnapshotResponse> history,
                         List<BreadthAlertResponse> alerts,
                         Map<Integer, BreadthScoreConfiguration> configurations) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            WorkbookStyles styles = styles(workbook);
            addHistorySheet(workbook, styles, history);
            addScoreSheet(workbook, styles, history, configurations);
            addSectorSheet(workbook, styles, history);
            addAlertSheet(workbook, styles, alerts);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Market Breadth Excel export could not be generated", exception);
        }
    }

    private void addHistorySheet(Workbook workbook, WorkbookStyles styles,
                                 List<BreadthSnapshotResponse> history) {
        Set<String> indicatorKeys = history.stream()
                .flatMap(snapshot -> safe(snapshot.indicators()).keySet().stream())
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> headers = new ArrayList<>(List.of("Date", "Index", "Methodology",
                "Score configuration version", "Coverage %", "Quality", "Breadth score", "Regime"));
        headers.addAll(indicatorKeys.stream().map(this::displayName).toList());
        Sheet sheet = tableSheet(workbook, "Breadth History", styles, headers);
        int rowIndex = 1;
        for (BreadthSnapshotResponse snapshot : history) {
            Row row = sheet.createRow(rowIndex++);
            date(row, 0, snapshot.tradingDate(), styles.date());
            text(row, 1, displayName(snapshot.universe().name()));
            text(row, 2, displayName(snapshot.methodology().name()));
            number(row, 3, snapshot.scoreConfigurationVersion(), styles.integer());
            number(row, 4, snapshot.coveragePercent(), styles.decimal());
            text(row, 5, snapshot.qualityStatus().name());
            number(row, 6, snapshot.score(), styles.decimal());
            text(row, 7, snapshot.regime().name());
            int column = 8;
            Map<String, Double> indicators = safe(snapshot.indicators());
            for (String key : indicatorKeys) {
                Double value = indicators.get(key);
                if (value != null) number(row, column, value, styles.decimal());
                column++;
            }
        }
        finishTable(sheet, headers.size(), rowIndex);
    }

    private void addScoreSheet(Workbook workbook, WorkbookStyles styles,
                               List<BreadthSnapshotResponse> history,
                               Map<Integer, BreadthScoreConfiguration> configurations) {
        List<String> headers = List.of("Date", "Index", "Component", "Points", "Weight",
                "Configuration version", "Reason");
        Sheet sheet = tableSheet(workbook, "Score Details", styles, headers);
        int rowIndex = 1;
        for (BreadthSnapshotResponse snapshot : history) {
            Set<String> components = new TreeSet<>();
            components.addAll(safe(snapshot.componentScores()).keySet());
            components.addAll(safeString(snapshot.componentReasons()).keySet());
            Map<String, Integer> weights = configurations.containsKey(snapshot.scoreConfigurationVersion())
                    ? safeInteger(configurations.get(snapshot.scoreConfigurationVersion()).getWeights()) : Map.of();
            for (String component : components) {
                Row row = sheet.createRow(rowIndex++);
                date(row, 0, snapshot.tradingDate(), styles.date());
                text(row, 1, displayName(snapshot.universe().name()));
                text(row, 2, displayName(component));
                Double points = safe(snapshot.componentScores()).get(component);
                if (points != null) number(row, 3, points, styles.decimal());
                Integer weight = weights.get(component);
                if (weight != null) number(row, 4, weight, styles.integer());
                number(row, 5, snapshot.scoreConfigurationVersion(), styles.integer());
                text(row, 6, safeString(snapshot.componentReasons()).getOrDefault(component, ""));
            }
        }
        finishTable(sheet, headers.size(), rowIndex);
    }

    private void addSectorSheet(Workbook workbook, WorkbookStyles styles,
                                List<BreadthSnapshotResponse> history) {
        List<String> headers = List.of("Date", "Index", "Sector", "Above 50-day EMA",
                "Distance from 50-day EMA %");
        Sheet sheet = tableSheet(workbook, "Sector Participation", styles, headers);
        int rowIndex = 1;
        for (BreadthSnapshotResponse snapshot : history) {
            Map<String, Double> indicators = safe(snapshot.indicators());
            for (RequiredMarketBreadthIndex sector : RequiredMarketBreadthIndex.sectorIndices()) {
                String stateKey = "SECTOR_" + sector.name() + "_ABOVE_50D";
                String distanceKey = "SECTOR_" + sector.name() + "_DISTANCE_PERCENT";
                if (!indicators.containsKey(stateKey) && !indicators.containsKey(distanceKey)) continue;
                Row row = sheet.createRow(rowIndex++);
                date(row, 0, snapshot.tradingDate(), styles.date());
                text(row, 1, displayName(snapshot.universe().name()));
                text(row, 2, sector.getSymbol());
                text(row, 3, indicators.getOrDefault(stateKey, 0.0) > 0.5 ? "Yes" : "No");
                number(row, 4, indicators.getOrDefault(distanceKey, 0.0), styles.decimal());
            }
        }
        finishTable(sheet, headers.size(), rowIndex);
    }

    private void addAlertSheet(Workbook workbook, WorkbookStyles styles,
                               List<BreadthAlertResponse> alerts) {
        List<String> headers = List.of("Date", "Index", "Alert type", "Message",
                "Delivery state", "Created at", "Trigger values");
        Sheet sheet = tableSheet(workbook, "Alerts", styles, headers);
        int rowIndex = 1;
        for (BreadthAlertResponse alert : alerts) {
            Row row = sheet.createRow(rowIndex++);
            date(row, 0, alert.triggerDate(), styles.date());
            text(row, 1, displayName(alert.universe().name()));
            text(row, 2, displayName(alert.alertType().name()));
            text(row, 3, alert.message());
            text(row, 4, alert.deliveryState() == null ? AlertDeliveryState.PENDING.name()
                    : alert.deliveryState().name());
            text(row, 5, alert.createdAt() == null ? "" : alert.createdAt().toString());
            text(row, 6, mapText(alert.triggerValues()));
        }
        finishTable(sheet, headers.size(), rowIndex);
    }

    private Sheet tableSheet(Workbook workbook, String name, WorkbookStyles styles,
                             List<String> headers) {
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        header.setHeightInPoints(24);
        for (int column = 0; column < headers.size(); column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(headers.get(column));
            cell.setCellStyle(styles.header());
        }
        sheet.createFreezePane(2, 1);
        return sheet;
    }

    private void finishTable(Sheet sheet, int columnCount, int rowCount) {
        if (rowCount > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, rowCount - 1, 0, columnCount - 1));
        }
        for (int column = 0; column < columnCount; column++) {
            int longestValue = 10;
            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                Cell cell = row == null ? null : row.getCell(column);
                if (cell != null) longestValue = Math.max(longestValue, cell.toString().length());
            }
            sheet.setColumnWidth(column, Math.min((longestValue + 2) * 256, 12000));
        }
    }

    private WorkbookStyles styles(Workbook workbook) {
        CellStyle header = workbook.createCellStyle();
        header.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setBorderBottom(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        header.setFont(font);
        CellStyle date = workbook.createCellStyle();
        date.setDataFormat(workbook.createDataFormat().getFormat("dd-mmm-yyyy"));
        CellStyle decimal = workbook.createCellStyle();
        decimal.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        CellStyle integer = workbook.createCellStyle();
        integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        return new WorkbookStyles(header, date, decimal, integer);
    }

    private void date(Row row, int column, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void text(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private void number(Row row, int column, double value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String displayName(String value) {
        String normalized = value.replace("50D", "50-day").replace("200D", "200-day");
        String[] words = normalized.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private String mapText(Map<String, Double> values) {
        return new TreeSet<>(safe(values).keySet()).stream()
                .map(key -> key + "=" + safe(values).get(key))
                .collect(Collectors.joining("; "));
    }

    private Map<String, Double> safe(Map<String, Double> values) {
        return values == null ? Map.of() : values;
    }

    private Map<String, Integer> safeInteger(Map<String, Integer> values) {
        return values == null ? Map.of() : values;
    }

    private Map<String, String> safeString(Map<String, String> values) {
        return values == null ? Map.of() : values;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("A valid Market Breadth export date range is required");
        }
        if (to.isAfter(from.plusYears(10))) {
            throw new IllegalArgumentException("Market Breadth export date range cannot exceed 10 years");
        }
    }

    private record WorkbookStyles(CellStyle header, CellStyle date, CellStyle decimal, CellStyle integer) {
    }

    public record ExportResult(byte[] content, LocalDate actualFrom, LocalDate actualTo) {
    }
}
