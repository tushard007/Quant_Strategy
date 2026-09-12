package org.factor_investing.quant_strategy.model.response;

public record NiftyIndexStockImportResponse(String indexName, int totalRows, String message) {
}
