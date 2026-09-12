package org.factor_investing.quant_strategy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.service.MarketBreadthExcelExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/market-breadth/export")
@Tag(name = "Market Breadth Export", description = "Excel export for Market Breadth dashboard data")
public class MarketBreadthExportController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final MarketBreadthExcelExportService exportService;

    public MarketBreadthExportController(MarketBreadthExcelExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping
    @Operation(summary = "Export the selected Market Breadth period as an Excel workbook")
    public ResponseEntity<byte[]> export(
            @RequestParam NiftyIndexName universe,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var export = exportService.export(universe, from, to);
        String fileName = "market-breadth-" + universe.name().toLowerCase()
                + "-" + export.actualFrom() + "-to-" + export.actualTo() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(XLSX)
                .body(export.content());
    }
}
