package org.factor_investing.quant_strategy.util;

import java.text.DecimalFormat;

public class ReturnCalculationUtils {

    public static float  percentReturn(Float startingPrice,Float closingPrice){
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        float priceReturn=((closingPrice-startingPrice)/startingPrice)*100;
        return Float.parseFloat(df.format(priceReturn));
    }
}