package org.factor_investing.quant_strategy.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.factor_investing.quant_strategy.model.NiftyIndexStock;
import org.factor_investing.quant_strategy.model.NSEStockMasterData;
import org.factor_investing.quant_strategy.model.response.NiftyIndexStockDetail;
import org.factor_investing.quant_strategy.model.response.NiftyIndexStockImportResponse;
import org.factor_investing.quant_strategy.repository.NiftyIndexRepository;
import org.factor_investing.quant_strategy.repository.NSEStockMasterDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class NiftyIndexStockService {
    private static final Map<NiftyIndexName, Function<NiftyIndexStock, String>> GETTERS = Map.of(
            NiftyIndexName.NIFTY50, NiftyIndexStock::getNifty50,
            NiftyIndexName.NIFTY500, NiftyIndexStock::getNifty500,
            NiftyIndexName.NIFTY750, NiftyIndexStock::getNifty750,
            NiftyIndexName.NIFTY_MIDCAP150, NiftyIndexStock::getNiftyMidcap150,
            NiftyIndexName.NIFTY_NEXT50, NiftyIndexStock::getNiftyNext50,
            NiftyIndexName.NIFTY_SMALLCAP250, NiftyIndexStock::getNiftySmallcap250,
            NiftyIndexName.NIFTY200, NiftyIndexStock::getNifty200
    );
    private static final Map<NiftyIndexName, BiConsumer<NiftyIndexStock, String>> SETTERS = Map.of(
            NiftyIndexName.NIFTY50, NiftyIndexStock::setNifty50,
            NiftyIndexName.NIFTY500, NiftyIndexStock::setNifty500,
            NiftyIndexName.NIFTY750, NiftyIndexStock::setNifty750,
            NiftyIndexName.NIFTY_MIDCAP150, NiftyIndexStock::setNiftyMidcap150,
            NiftyIndexName.NIFTY_NEXT50, NiftyIndexStock::setNiftyNext50,
            NiftyIndexName.NIFTY_SMALLCAP250, NiftyIndexStock::setNiftySmallcap250,
            NiftyIndexName.NIFTY200, NiftyIndexStock::setNifty200
    );

    private final NiftyIndexRepository niftyIndexRepository;
    private final NSEStockMasterDataRepository stockMasterRepository;

    public NiftyIndexStockService(NiftyIndexRepository niftyIndexRepository, NSEStockMasterDataRepository stockMasterRepository) {
        this.niftyIndexRepository = niftyIndexRepository;
        this.stockMasterRepository = stockMasterRepository;
    }

    public List<NiftyIndexStockDetail> listForIndex(NiftyIndexName index) {
        return symbolsForIndex(index).stream().map(this::toDetail).toList();
    }

    public List<String> symbolsForIndex(NiftyIndexName index) {
        Function<NiftyIndexStock, String> getter = GETTERS.get(index);
        return niftyIndexRepository.findAll().stream()
                .map(getter)
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(symbol -> symbol.trim().toUpperCase())
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    @Transactional
    public NiftyIndexStockImportResponse replaceIndexColumn(NiftyIndexName index, List<String> symbols) {
        List<String> normalized = symbols.stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(symbol -> symbol.trim().toUpperCase())
                .distinct()
                .toList();
        List<NiftyIndexStock> existing = niftyIndexRepository.findAll();
        Map<NiftyIndexName, List<String>> columnValues = new java.util.HashMap<>();
        for (NiftyIndexName name : NiftyIndexName.values()) {
            if (name == index) {
                columnValues.put(name, normalized);
            } else {
                Function<NiftyIndexStock, String> getter = GETTERS.get(name);
                columnValues.put(name, existing.stream().map(getter).filter(v -> v != null && !v.isBlank()).toList());
            }
        }
        int maxRows = columnValues.values().stream().mapToInt(List::size).max().orElse(0);
        List<NiftyIndexStock> rebuilt = new ArrayList<>();
        for (int i = 0; i < maxRows; i++) {
            NiftyIndexStock row = new NiftyIndexStock();
            for (NiftyIndexName name : NiftyIndexName.values()) {
                List<String> values = columnValues.get(name);
                SETTERS.get(name).accept(row, i < values.size() ? values.get(i) : null);
            }
            rebuilt.add(row);
        }
        niftyIndexRepository.deleteAll();
        niftyIndexRepository.flush();
        niftyIndexRepository.saveAll(rebuilt);
        return new NiftyIndexStockImportResponse(index.name(), normalized.size(), "Stock list for " + index.name() + " was replaced successfully");
    }

    @Transactional
    public NiftyIndexStockImportResponse replaceIndexColumnFromCsv(NiftyIndexName index, MultipartFile file) {
        if (file == null || file.isEmpty()) throw csvError("Please select a non-empty CSV file");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".csv")) throw csvError("Please upload a .csv file");
        return replaceIndexColumn(index, parseSymbols(file));
    }

    private List<String> parseSymbols(MultipartFile file) {
        try (BufferedReader buffered = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)); CSVReader reader = new CSVReader(buffered)) {
            String[] headers = reader.readNext();
            if (headers == null) throw csvError("CSV file does not contain a header row");
            List<String> result = new ArrayList<>();
            String[] values;
            while ((values = reader.readNext()) != null) {
                if (values.length == 0 || values[0] == null || values[0].isBlank()) continue;
                result.add(values[0].trim().toUpperCase());
            }
            if (result.isEmpty()) throw csvError("CSV must contain at least one symbol");
            return result;
        } catch (IOException | CsvValidationException exception) {
            throw csvError("CSV file could not be read: " + exception.getMessage());
        }
    }

    private NiftyIndexStockDetail toDetail(String symbol) {
        return stockMasterRepository.findBySymbolIgnoreCase(symbol)
                .map(this::fromStockMaster)
                .orElse(new NiftyIndexStockDetail(symbol, "(not found in Stock Master)", "-", "-", "-"));
    }

    private NiftyIndexStockDetail fromStockMaster(NSEStockMasterData data) {
        return new NiftyIndexStockDetail(data.getSymbol(), data.getNameOfCompany(), data.getSeries(), data.getIsinNumber(), data.getIndustry());
    }

    private ResponseStatusException csvError(String message) {
        return new ResponseStatusException(BAD_REQUEST, message);
    }
}
