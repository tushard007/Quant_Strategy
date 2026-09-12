package org.factor_investing.quant_strategy.repository;

import org.factor_investing.quant_strategy.model.IndexPricesJson;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IndexPriceDataRepository extends JpaRepository<IndexPricesJson, Long> {
    List<IndexPricesJson> findAllByTimeFrame(PriceFrequencey timeFrame);

    Optional<IndexPricesJson> findByNseIndexMasterData_SymbolIgnoreCaseAndTimeFrame(String symbol, PriceFrequencey timeFrame);

    Optional<IndexPricesJson> findByNseIndexMasterData_IdAndTimeFrame(Long indexMasterId, PriceFrequencey timeFrame);
}
