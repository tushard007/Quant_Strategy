package org.factor_investing.quant_strategy.controller;

import lombok.extern.slf4j.Slf4j;
import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.technical_analysis.TechnicalIndicatorService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/technical-indicator")
public class TechnicalIndicatorController {
    private final TechnicalIndicatorService technicalIndicatorService;

    public TechnicalIndicatorController(TechnicalIndicatorService technicalIndicatorService) {
        this.technicalIndicatorService = technicalIndicatorService;
    }

    @GetMapping("EMAIndicator/{days}")
    public ResponseEntity<?> calculateEMAIndicator(@PathVariable(required = true) int days, @RequestParam(required = false) String download, @RequestParam(required = true)AssetDataType assetDataType) {
        Map<String, List<Double>> emaResults = technicalIndicatorService.calculateLatestEma(days,assetDataType);
        // Check if Excel download is requested
        if ("excel".equalsIgnoreCase(download)) {
            // Generate Excel file
            byte[] excelData = technicalIndicatorService.generateExcel(emaResults,days);

            // Create filename with timestamp
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
            String filename = assetDataType.toString()+" EMA_" + days + " " + timestamp + ".xlsx";

            // Set response headers for Excel download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(excelData.length);

            // Return Excel file - Browser will automatically download it
            return new ResponseEntity<>(excelData, headers, HttpStatus.OK);
        }

        // Return JSON response (default)
        return ResponseEntity.ok(emaResults);

    }
}