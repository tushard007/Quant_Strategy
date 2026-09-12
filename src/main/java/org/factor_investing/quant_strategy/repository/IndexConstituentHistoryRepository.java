package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.IndexConstituentHistory;
import org.factor_investing.quant_strategy.model.NiftyIndexName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface IndexConstituentHistoryRepository extends JpaRepository<IndexConstituentHistory, UUID> {
    List<IndexConstituentHistory> findByIndexNameAndStockSymbol(NiftyIndexName indexName, String stockSymbol);

    /**
     * Point-in-time membership: rows in effect (or still open-ended) on the given date.
     * A derived-method name can't express this AND/OR grouping correctly, hence the explicit query.
     */
    @Query("select h from IndexConstituentHistory h where h.indexName = :indexName "
            + "and h.effectiveFrom <= :asOfDate and (h.effectiveTo is null or h.effectiveTo >= :asOfDate)")
    List<IndexConstituentHistory> findMembersAsOf(@Param("indexName") NiftyIndexName indexName, @Param("asOfDate") LocalDate asOfDate);

    boolean existsByIndexNameAndStockSymbolAndEffectiveFrom(NiftyIndexName indexName, String stockSymbol, LocalDate effectiveFrom);
}
