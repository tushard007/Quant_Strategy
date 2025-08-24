package org.factor_investing.quant_strategy.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class JGetHistoricalCandleResponse {
    @JsonProperty("status")
    private String status;

    String fullName;
    String symbol;
    @JsonProperty("data")
    private List<CandleData> data;


    @Data
    public static class CandleData {

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private Date priceDate;

        @JsonProperty("open")
        private Double open;

        @JsonProperty("high")
        private Double high;

        @JsonProperty("low")
        private Double low;

        @JsonProperty("close")
        private Double close;

        @JsonProperty("volume")
        private Long volume;
    }
}