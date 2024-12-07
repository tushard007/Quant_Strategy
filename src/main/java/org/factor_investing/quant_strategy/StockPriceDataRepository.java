package org.factor_investing.quant_strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Set;

@Repository
public interface StockPriceDataRepository extends JpaRepository<StockPriceData, Integer> {

    @Query("SELECT DISTINCT sp.stockTicker FROM StockPriceData sp")
    Set<String> findDistinctByStockTicker();
    @Query("SELECT DISTINCT sp.priceDate FROM StockPriceData sp")
    Set<Date> findDistinctByPriceDate();
    StockPriceData findByStockTickerAndPriceDate(String stockTicker,java.sql.Date priceDate);
}