package org.factor_investing.quant_strategy.service;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.factor_investing.quant_strategy.model.AlertDeliveryState;
import org.factor_investing.quant_strategy.model.BreadthAlertType;
import org.factor_investing.quant_strategy.model.BreadthRegime;
import org.factor_investing.quant_strategy.model.BreadthScoreConfiguration;
import org.factor_investing.quant_strategy.model.BreadthSnapshotQuality;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.response.BreadthAlertResponse;
import org.factor_investing.quant_strategy.model.response.BreadthSnapshotResponse;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMethodology;
import org.factor_investing.quant_strategy.strategies.market_breadth.BreadthMetricKeys;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketBreadthExcelExportServiceTest {

    @Test
    void exportsScreenDataToStructuredWorkbook() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 11);
        BreadthSnapshotResponse snapshot = new BreadthSnapshotResponse(
                UUID.randomUUID(), NiftyIndexName.NIFTY500, date,
                BreadthMethodology.CURRENT_CONSTITUENTS, 1, 97.0,
                BreadthSnapshotQuality.VALID, 72.5, BreadthRegime.GREEN,
                Map.of(BreadthMetricKeys.BREADTH_50D_PERCENT, 63.25,
                        "SECTOR_NIFTY_BANK_ABOVE_50D", 1.0,
                        "SECTOR_NIFTY_BANK_DISTANCE_PERCENT", 2.75),
                Map.of("BREADTH_50D", 15.0),
                Map.of("BREADTH_50D", "63.3% of stocks are above the 50-day EMA"));
        BreadthAlertResponse alert = new BreadthAlertResponse(
                UUID.randomUUID(), BreadthAlertType.BREADTH_50D_CROSS_60_UP,
                NiftyIndexName.NIFTY500, date,
                Map.of(BreadthMetricKeys.BREADTH_50D_PERCENT, 63.25),
                "50-day breadth crossed above 60.0%", AlertDeliveryState.SENT, Instant.now());
        BreadthScoreConfiguration configuration = new BreadthScoreConfiguration();
        configuration.setVersion(1);
        configuration.setWeights(Map.of("BREADTH_50D", 15));

        MarketBreadthExcelExportService service = new MarketBreadthExcelExportService(null, null, null);
        byte[] bytes = service.buildWorkbook(java.util.List.of(snapshot), java.util.List.of(alert),
                Map.of(1, configuration));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(workbook.getSheetName(0)).isEqualTo("Breadth History");
            assertThat(workbook.getSheet("Breadth History").getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Date");
            assertThat(workbook.getSheet("Breadth History").getRow(0).getCell(1).getStringCellValue())
                    .isEqualTo("Index");
            assertThat(workbook.getSheet("Breadth History").getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheet("Score Details").getRow(1).getCell(4).getNumericCellValue())
                    .isEqualTo(15.0);
            assertThat(workbook.getSheet("Sector Participation").getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheet("Alerts").getLastRowNum()).isEqualTo(1);
        }
    }
}
