package org.factor_investing.quant_strategy.service;

import java.util.List;

/** Counts describe completed chunks, never uncommitted fetches. */
public record PriceImportProgress(int processed, int total, int saved, List<String> failedSymbols) {}
