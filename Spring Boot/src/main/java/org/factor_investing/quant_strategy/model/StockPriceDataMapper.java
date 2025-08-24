package org.factor_investing.quant_strategy.model;

import java.util.Date;

public record StockPriceDataMapper(String symbol,
                                    Date date,
                                  Double ClosePrice){

}