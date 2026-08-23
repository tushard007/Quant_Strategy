// java
package org.factor_investing.quant_strategy.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.factor_investing.quant_strategy.model.NSEStockMasterData;
import org.factor_investing.quant_strategy.model.request.StockMasterRequest;
import org.factor_investing.quant_strategy.model.response.StockMasterImportResponse;
import org.factor_investing.quant_strategy.repository.NSEStockMasterDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NSE_StockDataService {
    private static final List<String> CSV_HEADERS = List.of("Company Name", "Industry", "Symbol", "Series", "ISIN Code");
    private final NSEStockMasterDataRepository stockRepository;

    public NSE_StockDataService(NSEStockMasterDataRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public void saveStockData(NSEStockMasterData stockData) {
        stockRepository.save(stockData);
    }

    public NSEStockMasterData getStockDataById(Long id) {
        return stockRepository.findById(id).orElse(null);
    }

    public NSEStockMasterData requireStockDataById(Long id) {
        return stockRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Stock master record not found: " + id));
    }

    public void deleteStockData(Long id) {
        stockRepository.delete(requireStockDataById(id));
    }

    public List<NSEStockMasterData> getAllStockData() {
        return stockRepository.findAll();
    }

    public void updateStockData(NSEStockMasterData stockData) {
        if (stockData == null || stockData.getId() == null) {
            throw new IllegalArgumentException("Stock data or ID must not be null.");
        }
        if (stockRepository.existsById(stockData.getId())) {
            stockRepository.save(stockData);
        } else {
            throw new IllegalArgumentException("Stock data with ID " + stockData.getId() + " does not exist.");
        }
    }

    public NSEStockMasterData getStockDataBySymbol(String symbol) {
        if (symbol == null) return null;
        return stockRepository.findBySymbolIgnoreCase(symbol.trim()).orElse(null);
    }

    public void deleteStockDataBySymbol(String symbol) {
        NSEStockMasterData stockData = getStockDataBySymbol(symbol);
        if (stockData != null) {
            stockRepository.delete(stockData);
        } else {
            throw new IllegalArgumentException("Stock data with symbol " + symbol + " does not exist.");
        }
    }

    public void saveAllStockData(Iterable<NSEStockMasterData> stockDataList) {
        stockRepository.saveAll(stockDataList);
    }

    public List<NSEStockMasterData> searchStockData(String search) {
        String term = search == null ? "" : search.trim().toLowerCase();
        return stockRepository.findAll().stream()
                .filter(stock -> term.isEmpty()
                        || contains(stock.getSymbol(), term)
                        || contains(stock.getNameOfCompany(), term)
                        || contains(stock.getIsinNumber(), term)
                        || contains(stock.getIndustry(), term))
                .sorted((left, right) -> left.getSymbol().compareToIgnoreCase(right.getSymbol()))
                .toList();
    }

    public NSEStockMasterData createStockData(StockMasterRequest request) {
        String symbol = normalize(request.symbol());
        if (stockRepository.existsBySymbolIgnoreCase(symbol)) {
            throw new ResponseStatusException(CONFLICT, "A stock with symbol " + symbol + " already exists");
        }
        return stockRepository.save(apply(new NSEStockMasterData(), request));
    }

    public NSEStockMasterData updateStockData(Long id, StockMasterRequest request) {
        NSEStockMasterData stock = requireStockDataById(id);
        String symbol = normalize(request.symbol());
        if (stockRepository.existsBySymbolIgnoreCaseAndIdNot(symbol, id)) {
            throw new ResponseStatusException(CONFLICT, "A stock with symbol " + symbol + " already exists");
        }
        return stockRepository.save(apply(stock, request));
    }

    private NSEStockMasterData apply(NSEStockMasterData stock, StockMasterRequest request) {
        stock.setSymbol(normalize(request.symbol()));
        stock.setNameOfCompany(request.nameOfCompany().trim());
        stock.setSeries(normalize(request.series()));
        stock.setIsinNumber(normalize(request.isinNumber()));
        stock.setIndustry(request.industry().trim());
        return stock;
    }

    private String normalize(String value) { return value.trim().toUpperCase(); }
    private boolean contains(String value, String term) { return value != null && value.toLowerCase().contains(term); }

    @Transactional
    public StockMasterImportResponse replaceFromCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Please select a non-empty CSV file");
        }
        List<StockMasterRequest> rows = parseCsv(file);
        List<NSEStockMasterData> existing = stockRepository.findAll();
        Map<String, NSEStockMasterData> existingBySymbol = existing.stream()
                .collect(Collectors.toMap(stock -> normalize(stock.getSymbol()), Function.identity()));
        Set<String> importedSymbols = new HashSet<>();
        List<NSEStockMasterData> replacements = new ArrayList<>();
        int created = 0;
        int updated = 0;

        for (StockMasterRequest row : rows) {
            String symbol = normalize(row.symbol());
            NSEStockMasterData stock = existingBySymbol.get(symbol);
            if (stock == null) { stock = new NSEStockMasterData(); created++; } else { updated++; }
            replacements.add(apply(stock, row));
            importedSymbols.add(symbol);
        }

        List<NSEStockMasterData> removed = existing.stream()
                .filter(stock -> !importedSymbols.contains(normalize(stock.getSymbol())))
                .toList();
        stockRepository.saveAll(replacements);
        stockRepository.flush();
        stockRepository.deleteAll(removed);
        stockRepository.flush();
        return new StockMasterImportResponse(rows.size(), created, updated, removed.size(),
                "Stock master data was replaced successfully");
    }

    private List<StockMasterRequest> parseCsv(MultipartFile file) {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVReader reader = new CSVReader(bufferedReader)) {
            String[] headers = reader.readNext();
            if (headers == null) throw csvError("CSV file does not contain a header row");
            headers[0] = headers[0].replace("\uFEFF", "");
            if (headers.length != CSV_HEADERS.size()) throw csvError("CSV must contain exactly 5 columns in this order: " + String.join(", ", CSV_HEADERS));
            for (int i = 0; i < CSV_HEADERS.size(); i++) {
                if (!CSV_HEADERS.get(i).equals(headers[i].trim())) throw csvError("Invalid column " + (i + 1) + ". Expected '" + CSV_HEADERS.get(i) + "' but found '" + headers[i].trim() + "'");
            }
            List<StockMasterRequest> rows = new ArrayList<>();
            Set<String> symbols = new HashSet<>();
            String[] values;
            int rowNumber = 1;
            while ((values = reader.readNext()) != null) {
                rowNumber++;
                if (values.length == 1 && values[0].isBlank()) continue;
                if (values.length != 5) throw csvError("Row " + rowNumber + " must contain exactly 5 columns");
                for (int column = 0; column < values.length; column++) {
                    if (values[column] == null || values[column].trim().isEmpty()) throw csvError("Row " + rowNumber + ", column '" + CSV_HEADERS.get(column) + "' is required");
                }
                String symbol = normalize(values[2]);
                if (!symbols.add(symbol)) throw csvError("Duplicate symbol '" + symbol + "' at row " + rowNumber);
                if (values[0].trim().length() > 255 || values[1].trim().length() > 150 || symbol.length() > 30 || values[3].trim().length() > 20 || values[4].trim().length() > 20) throw csvError("One or more values at row " + rowNumber + " exceed the allowed length");
                rows.add(new StockMasterRequest(symbol, values[0].trim(), normalize(values[3]), normalize(values[4]), values[1].trim()));
            }
            if (rows.isEmpty()) throw csvError("CSV must contain at least one data row");
            return rows;
        } catch (IOException | CsvValidationException exception) {
            throw csvError("CSV file could not be read: " + exception.getMessage());
        }
    }

    private ResponseStatusException csvError(String message) {
        return new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, message);
    }
}
