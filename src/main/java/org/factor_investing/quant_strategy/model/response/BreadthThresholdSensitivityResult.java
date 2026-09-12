package org.factor_investing.quant_strategy.model.response;

import org.factor_investing.quant_strategy.model.request.BreadthThresholdSensitivityRequest;

import java.util.List;

public record BreadthThresholdSensitivityResult(
        BreadthThresholdSensitivityRequest request, List<BreadthThresholdSensitivityCell> cells, List<String> warnings) {
}
