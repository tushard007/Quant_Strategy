package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.AssetDataType;
import java.time.LocalDate;
import java.util.List;

public record OhlcvExperimentResult(
        AssetDataType assetDataType,
        LocalDate asOfDate,
        int universeSize,
        int scoredCount,
        int skippedCount,
        List<Row> results,
        List<PortfolioPosition> portfolio
) {
    public record Row(
            String ticker, String theme, LocalDate asOfDate,
            double close, double ret12, double ret6, double ret3, double ret1d,
            double sma20, double sma50, double sma200, double sma50Prev,
            double high52, double pctFromHigh, double atr14, double extension,
            double vol20, double vol50, long lastVolume,
            int rank12, int rank6, int rank3,
            int p1, int p2, int p3, int p4, int p5, int score,
            String bucket, String action
    ) {}

    public record PortfolioPosition(String ticker, String theme, String bucket, double weightPercent, int score) {}
}
