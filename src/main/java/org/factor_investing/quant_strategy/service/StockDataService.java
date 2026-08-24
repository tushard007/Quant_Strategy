package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.ETFPricesJson;
import org.factor_investing.quant_strategy.model.IndexPricesJson;
import org.factor_investing.quant_strategy.model.StockPricesJson;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.factor_investing.quant_strategy.repository.ETFPriceDataRepository;
import org.factor_investing.quant_strategy.repository.IndexPriceDataRepository;
import org.factor_investing.quant_strategy.repository.StockDataRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockDataService {

    private final StockDataRepository stockDataRepository;
    private final ETFPriceDataRepository etfPriceDataRepository;
    private final IndexPriceDataRepository indexPriceDataRepository;

    public StockDataService(
            StockDataRepository stockDataRepository,
            ETFPriceDataRepository etfPriceDataRepository,
            IndexPriceDataRepository indexPriceDataRepository
    ) {
        this.stockDataRepository = stockDataRepository;
        this.etfPriceDataRepository = etfPriceDataRepository;
        this.indexPriceDataRepository = indexPriceDataRepository;
    }

    public List<StockPricesJson> getAllStockData() {
        return stockDataRepository.findAllByTimeFrame(PriceFrequencey.DAILY);
    }

    public List<ETFPricesJson> getAllETFData() {
        return etfPriceDataRepository.findAllByTimeFrame(PriceFrequencey.DAILY);
    }

    public List<IndexPricesJson> getAllIndexData() {
        return indexPriceDataRepository.findAllByTimeFrame(PriceFrequencey.DAILY);
    }
}
