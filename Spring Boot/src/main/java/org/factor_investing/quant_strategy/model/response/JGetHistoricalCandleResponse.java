package org.factor_investing.quant_strategy.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class JGetHistoricalCandleResponse {
    @JsonProperty("status")
    private String status;

    String nameOfCompany;
    @JsonProperty("data")
    private List<CandleData> data;

//    public JGetHistoricalCandleResponse(String timeStamp, double v, double v1, double v2, double v3, long l) {
//    }
//
//    public JGetHistoricalCandleResponse() {
//
//    }

    @Data
    public static class CandleData {



        @JsonProperty("timestamp")
        private String timestamp;

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