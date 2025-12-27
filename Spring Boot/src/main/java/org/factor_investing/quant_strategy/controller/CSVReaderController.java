package org.factor_investing.quant_strategy.controller;

import org.factor_investing.quant_strategy.service.CSVReaderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/read-csv")
public class CSVReaderController {
    private final CSVReaderService readerService;

    public CSVReaderController(CSVReaderService readerService) {
        this.readerService = readerService;
    }

    @GetMapping("/row/{filename}")
    public List<String[]> readRowWiseCsv(@PathVariable String filename) {
        try {
            return readerService.readRowWiseCSVFile(filename);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read CSV file: " + e.getMessage());
        }
    }

    @GetMapping("/Column/{filename}")
    public Map<String, List<String>> readColumnCsv(@PathVariable String filename) {
        try {
            return readerService.readColumnWiseCsv(filename);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read CSV file: " + e.getMessage());
        }
    }
}

