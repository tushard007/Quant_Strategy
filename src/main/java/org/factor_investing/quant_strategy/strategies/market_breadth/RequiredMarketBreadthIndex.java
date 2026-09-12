package org.factor_investing.quant_strategy.strategies.market_breadth;

import org.factor_investing.quant_strategy.model.NSEIndexMasterData;

import java.util.Arrays;
import java.util.Locale;

/**
 * Indices the market-breadth feature depends on. Names only — no instrument keys are guessed
 * here; {@link BreadthReferenceDataValidationService} reports which of these are missing from
 * {@code NSEIndexMasterData} so real instrument keys can be added via the existing Index Master
 * screen.
 */
public enum RequiredMarketBreadthIndex {
    NIFTY_50("NIFTY 50"),
    NIFTY_500("NIFTY 500"),
    NIFTY_200("NIFTY 200"),
    NIFTY_BANK("NIFTY BANK"),
    NIFTY_IT("NIFTY IT"),
    NIFTY_AUTO("NIFTY AUTO"),
    NIFTY_PHARMA("NIFTY PHARMA"),
    NIFTY_FMCG("NIFTY FMCG"),
    NIFTY_METAL("NIFTY METAL"),
    NIFTY_REALTY("NIFTY REALTY"),
    NIFTY_ENERGY("NIFTY ENERGY"),
    NIFTY_FIN_SERVICE("NIFTY FIN SERVICE", "NIFTY FINANCIAL SERVICES"),
    NIFTY_MEDIA("NIFTY MEDIA"),
    NIFTY_PSU_BANK("NIFTY PSU BANK"),
    INDIA_VIX("INDIA VIX");

    private final String symbol;
    private final java.util.Set<String> normalizedAliases;

    RequiredMarketBreadthIndex(String symbol, String... aliases) {
        this.symbol = symbol;
        this.normalizedAliases = new java.util.HashSet<>();
        this.normalizedAliases.add(normalize(symbol));
        Arrays.stream(aliases).map(RequiredMarketBreadthIndex::normalize)
                .forEach(this.normalizedAliases::add);
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean matches(NSEIndexMasterData master) {
        if (master == null) return false;
        return matchesValue(master.getSymbol()) || matchesValue(master.getIndexName())
                || matchesValue(instrumentName(master.getInstrumentKey()));
    }

    private boolean matchesValue(String value) {
        return value != null && normalizedAliases.contains(normalize(value));
    }

    private static String instrumentName(String instrumentKey) {
        if (instrumentKey == null) return null;
        int separator = instrumentKey.lastIndexOf('|');
        return separator < 0 ? instrumentKey : instrumentKey.substring(separator + 1);
    }

    private static String normalize(String value) {
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static java.util.List<RequiredMarketBreadthIndex> sectorIndices() {
        return java.util.List.of(NIFTY_BANK, NIFTY_IT, NIFTY_AUTO, NIFTY_PHARMA, NIFTY_FMCG,
                NIFTY_METAL, NIFTY_REALTY, NIFTY_ENERGY, NIFTY_FIN_SERVICE, NIFTY_MEDIA, NIFTY_PSU_BANK);
    }
}
