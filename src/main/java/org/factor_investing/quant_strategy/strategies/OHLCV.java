package org.factor_investing.quant_strategy.strategies;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OHLCV {
    private Date date;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;


    @Override
    public String toString() {
        return String.format("OHLC{date=%s, open=%.2f, high=%.2f, low=%.2f, close=%.2f}",
                date, open, high, low, close);
    }
}
