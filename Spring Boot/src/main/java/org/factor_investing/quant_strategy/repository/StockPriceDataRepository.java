package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.StockPriceData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("SELECT DISTINCT s.priceDate FROM StockPriceData s " +
            "WHERE s.priceDate BETWEEN :startDate AND :endDate AND TRIM(TO_CHAR(s.priceDate, 'Day')) = 'Friday' ORDER BY s.priceDate")
    Set<java.sql.Date> findDistinctBetweenDateOfEachFriday(@Param("startDate") java.sql.Date startDate, @Param("endDate") java.sql.Date endDate);

    @Query(value = "SELECT DISTINCT ON (date_trunc('month', date)) date AS last_date " +
            "FROM stock_price_data where date BETWEEN :startDate AND :endDate" +
            " ORDER BY date_trunc('month', date), date DESC;", nativeQuery = true)
    Set<java.sql.Date> findDistinctLastDateOfEachMonth(@Param("startDate") java.sql.Date startDate, @Param("endDate") java.sql.Date endDate);}