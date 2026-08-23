package org.factor_investing.quant_strategy.model.response;

public record ETFMasterImportResponse(int totalRecords, int created, int updated, int removed, String message) {
}
