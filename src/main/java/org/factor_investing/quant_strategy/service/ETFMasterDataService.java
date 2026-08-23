package org.factor_investing.quant_strategy.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.factor_investing.quant_strategy.model.NSE_ETFMasterData;
import org.factor_investing.quant_strategy.model.request.ETFMasterRequest;
import org.factor_investing.quant_strategy.model.response.ETFMasterImportResponse;
import org.factor_investing.quant_strategy.repository.NSE_ETFMasterDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ETFMasterDataService {
    private static final List<String> CSV_HEADERS = List.of("Symbol", "Underlying", "SecurityName", "DateofListing", "MarketLot", "ISINNumber", "FaceValue");
    private final NSE_ETFMasterDataRepository repository;

    public ETFMasterDataService(NSE_ETFMasterDataRepository repository) { this.repository = repository; }

    public List<NSE_ETFMasterData> search(String search) {
        String term = search == null ? "" : search.trim().toLowerCase();
        return repository.findAll().stream().filter(etf -> term.isEmpty() || contains(etf.getSymbol(), term) || contains(etf.getSecurityName(), term) || contains(etf.getUnderlying(), term) || contains(etf.getIsinNumber(), term)).sorted((a, b) -> a.getSymbol().compareToIgnoreCase(b.getSymbol())).toList();
    }

    public NSE_ETFMasterData requireById(Long id) { return repository.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "ETF master record not found: " + id)); }

    public NSE_ETFMasterData create(ETFMasterRequest request) {
        String symbol = normalize(request.symbol());
        if (repository.existsBySymbolIgnoreCase(symbol)) throw new ResponseStatusException(CONFLICT, "An ETF with symbol " + symbol + " already exists");
        return repository.save(apply(new NSE_ETFMasterData(), request));
    }

    public NSE_ETFMasterData update(Long id, ETFMasterRequest request) {
        NSE_ETFMasterData etf = requireById(id);
        String symbol = normalize(request.symbol());
        if (repository.existsBySymbolIgnoreCaseAndIdNot(symbol, id)) throw new ResponseStatusException(CONFLICT, "An ETF with symbol " + symbol + " already exists");
        return repository.save(apply(etf, request));
    }

    public void delete(Long id) { repository.delete(requireById(id)); }

    @Transactional
    public ETFMasterImportResponse replaceFromCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) throw csvError("Please select a non-empty CSV file");
        List<ETFMasterRequest> rows = parseCsv(file);
        List<NSE_ETFMasterData> existing = repository.findAll();
        Map<String, NSE_ETFMasterData> existingBySymbol = existing.stream().collect(Collectors.toMap(etf -> normalize(etf.getSymbol()), Function.identity()));
        Set<String> imported = new HashSet<>();
        List<NSE_ETFMasterData> replacements = new ArrayList<>();
        int created = 0, updated = 0;
        for (ETFMasterRequest row : rows) {
            String symbol = normalize(row.symbol());
            NSE_ETFMasterData etf = existingBySymbol.get(symbol);
            if (etf == null) { etf = new NSE_ETFMasterData(); created++; } else updated++;
            replacements.add(apply(etf, row)); imported.add(symbol);
        }
        List<NSE_ETFMasterData> removed = existing.stream().filter(etf -> !imported.contains(normalize(etf.getSymbol()))).toList();
        repository.saveAll(replacements); repository.flush(); repository.deleteAll(removed); repository.flush();
        return new ETFMasterImportResponse(rows.size(), created, updated, removed.size(), "ETF master data was replaced successfully");
    }

    private List<ETFMasterRequest> parseCsv(MultipartFile file) {
        try (BufferedReader buffered = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)); CSVReader reader = new CSVReader(buffered)) {
            String[] headers = reader.readNext();
            if (headers == null) throw csvError("CSV file does not contain a header row");
            headers[0] = headers[0].replace("\uFEFF", "");
            if (headers.length != CSV_HEADERS.size()) throw csvError("CSV must contain exactly 7 columns in this order: " + String.join(", ", CSV_HEADERS));
            for (int i = 0; i < CSV_HEADERS.size(); i++) if (!CSV_HEADERS.get(i).equals(headers[i].trim())) throw csvError("Invalid column " + (i + 1) + ". Expected '" + CSV_HEADERS.get(i) + "' but found '" + headers[i].trim() + "'");
            List<ETFMasterRequest> rows = new ArrayList<>(); Set<String> symbols = new HashSet<>(); String[] values; int row = 1;
            while ((values = reader.readNext()) != null) {
                row++; if (values.length == 1 && values[0].isBlank()) continue;
                if (values.length != 7) throw csvError("Row " + row + " must contain exactly 7 columns");
                for (int col = 0; col < values.length; col++) if (values[col] == null || values[col].trim().isEmpty()) throw csvError("Row " + row + ", column '" + CSV_HEADERS.get(col) + "' is required");
                String symbol = normalize(values[0]); if (!symbols.add(symbol)) throw csvError("Duplicate symbol '" + symbol + "' at row " + row);
                try {
                    int marketLot = Integer.parseInt(values[4].trim()); double faceValue = Double.parseDouble(values[6].trim());
                    if (marketLot <= 0 || faceValue < 0) throw new NumberFormatException();
                    rows.add(new ETFMasterRequest(symbol, values[1].trim(), values[2].trim(), values[3].trim(), marketLot, normalize(values[5]), faceValue));
                } catch (NumberFormatException exception) { throw csvError("Row " + row + " contains an invalid Market Lot or Face Value"); }
            }
            if (rows.isEmpty()) throw csvError("CSV must contain at least one data row"); return rows;
        } catch (IOException | CsvValidationException exception) { throw csvError("CSV file could not be read: " + exception.getMessage()); }
    }

    private NSE_ETFMasterData apply(NSE_ETFMasterData etf, ETFMasterRequest request) { etf.setSymbol(normalize(request.symbol())); etf.setUnderlying(request.underlying().trim()); etf.setSecurityName(request.securityName().trim()); etf.setDateOfListing(request.dateOfListing().trim()); etf.setMarketLot(request.marketLot()); etf.setIsinNumber(normalize(request.isinNumber())); etf.setFaceValue(request.faceValue()); return etf; }
    private String normalize(String value) { return value.trim().toUpperCase(); }
    private boolean contains(String value, String term) { return value != null && value.toLowerCase().contains(term); }
    private ResponseStatusException csvError(String message) { return new ResponseStatusException(BAD_REQUEST, message); }
}
