package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.NiftyIndexStock;
import org.factor_investing.quant_strategy.repository.NiftyIndexRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NiftyIndexService {
    @Autowired
    private NiftyIndexRepository niftyIndexRepository;

    public List<NiftyIndexStock> getNiftyIndexData() {
        return niftyIndexRepository.findAll();
    }

    public Map<String, List<String>> getNiftyIndexDataMap() {
        List<NiftyIndexStock> niftyIndexStocksList = niftyIndexRepository.findAll();
        Map<String, List<String>> alldata = new LinkedHashMap<>();

        List<String> nifty50List = niftyIndexStocksList.stream()
                .map(NiftyIndexStock::getNifty50)
                .filter(Objects::nonNull) // Exclude nulls if any
                .distinct()
                .toList();

        List<String> niftyNext50List = niftyIndexStocksList.stream()
                .map(NiftyIndexStock::getNiftyNext50)
                .filter(Objects::nonNull) // Exclude nulls if any
                .distinct()
                .toList();
        List<String> niftyMidcap150List = niftyIndexStocksList.stream()
                .map(NiftyIndexStock::getNiftyMidcap150)
                .filter(Objects::nonNull) // Exclude nulls if any
                .distinct()
                .toList();
        List<String> niftySamllcap250List = niftyIndexStocksList.stream()
                .map(NiftyIndexStock::getNiftySmallcap250)
                .filter(Objects::nonNull) // Exclude nulls if any
                .distinct()
                .toList();
        List<String> nifty500List = niftyIndexStocksList.stream()
                .map(NiftyIndexStock::getNifty500)
                .filter(Objects::nonNull) // Exclude nulls if any
                .distinct()
                .toList();
        List<String> nifty750List = niftyIndexStocksList.stream()
                .map(NiftyIndexStock::getNifty750)
                .filter(Objects::nonNull) // Exclude nulls if any
                .distinct()
                .toList();

        alldata.put("Nifty50", nifty50List);
        alldata.put("NiftyNext50", niftyNext50List);
        alldata.put("NiftyMidcap150", niftyMidcap150List);
        alldata.put("NiftySmallcap250", niftySamllcap250List);
        alldata.put("Nifty500", nifty500List);
        alldata.put("Nifty750", nifty750List);

        return alldata;
    }
}