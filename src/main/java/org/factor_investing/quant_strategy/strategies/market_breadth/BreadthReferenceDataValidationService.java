package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.IndexPricesJson;
import org.factor_investing.quant_strategy.model.NSEIndexMasterData;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.factor_investing.quant_strategy.repository.IndexPriceDataRepository;
import org.factor_investing.quant_strategy.repository.NSEIndexMasterDataRepository;
import org.factor_investing.quant_strategy.strategies.OHLCV;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Read-only diagnostic check for whether the indices required by the market-breadth feature
 * (BRD-015) exist in {@code NSEIndexMasterData} and have recent daily price history. Never
 * creates or guesses instrument keys — only reports gaps for a human to fill via the existing
 * Index Master screen.
 */
@Service
public class BreadthReferenceDataValidationService {

    /** Daily history older than this many days is reported as stale rather than merely present. */
    private static final int STALE_HISTORY_THRESHOLD_DAYS = 10;
    private static final ZoneId INDIA_TIME_ZONE = ZoneId.of("Asia/Kolkata");

    private final NSEIndexMasterDataRepository nseIndexMasterDataRepository;
    private final IndexPriceDataRepository indexPriceDataRepository;

    public BreadthReferenceDataValidationService(NSEIndexMasterDataRepository nseIndexMasterDataRepository,
                                                   IndexPriceDataRepository indexPriceDataRepository) {
        this.nseIndexMasterDataRepository = nseIndexMasterDataRepository;
        this.indexPriceDataRepository = indexPriceDataRepository;
    }

    public BreadthReferenceDataReport checkRequiredIndices() {
        return checkRequiredIndices(LocalDate.now(INDIA_TIME_ZONE));
    }

    BreadthReferenceDataReport checkRequiredIndices(LocalDate currentDate) {
        List<IndexReferenceDataIssue> issues = new ArrayList<>();
        List<NSEIndexMasterData> masterRows = nseIndexMasterDataRepository.findAll();

        for (RequiredMarketBreadthIndex requiredIndex : RequiredMarketBreadthIndex.values()) {
            String symbol = requiredIndex.getSymbol();
            Optional<NSEIndexMasterData> master = masterRows.stream().filter(requiredIndex::matches).findFirst();
            if (master.isEmpty()) {
                issues.add(new IndexReferenceDataIssue(symbol, IndexReferenceDataIssue.IssueType.MISSING_INSTRUMENT,
                        "No NSEIndexMasterData row for this symbol; add it via the Index Master screen"));
                continue;
            }
            if (master.get().getInstrumentKey() == null || master.get().getInstrumentKey().isBlank()) {
                issues.add(new IndexReferenceDataIssue(symbol,
                        IndexReferenceDataIssue.IssueType.MISSING_INSTRUMENT_KEY,
                        "The index exists but has no Upstox instrument key"));
                continue;
            }

            Optional<IndexPricesJson> dailyPrices = indexPriceDataRepository
                    .findByNseIndexMasterData_IdAndTimeFrame(master.get().getId(), PriceFrequencey.DAILY);
            if (dailyPrices.isEmpty() || dailyPrices.get().getOhlcvData() == null || dailyPrices.get().getOhlcvData().isEmpty()) {
                issues.add(new IndexReferenceDataIssue(symbol, IndexReferenceDataIssue.IssueType.MISSING_HISTORY,
                        "No daily OHLCV history found for this index"));
                continue;
            }

            List<OHLCV> ohlcvData = dailyPrices.get().getOhlcvData();
            OHLCV latest = ohlcvData.stream().max(Comparator.comparing(OHLCV::getDate)).orElseThrow();
            LocalDate latestDate = latest.getDate().toInstant().atZone(INDIA_TIME_ZONE).toLocalDate();
            long daysSinceLatest = ChronoUnit.DAYS.between(latestDate, currentDate);
            if (daysSinceLatest > STALE_HISTORY_THRESHOLD_DAYS) {
                issues.add(new IndexReferenceDataIssue(symbol, IndexReferenceDataIssue.IssueType.STALE_HISTORY,
                        "Latest daily bar is " + daysSinceLatest + " days old"));
            }
        }

        return new BreadthReferenceDataReport(issues);
    }
}
