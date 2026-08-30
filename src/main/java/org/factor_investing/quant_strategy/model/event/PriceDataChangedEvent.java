package org.factor_investing.quant_strategy.model.event;

import org.factor_investing.quant_strategy.model.AssetDataType;

public record PriceDataChangedEvent(AssetDataType assetDataType) {
}
