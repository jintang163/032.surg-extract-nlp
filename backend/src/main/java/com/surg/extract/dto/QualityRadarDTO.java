package com.surg.extract.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class QualityRadarDTO {
    private List<String> indicatorNames;
    private List<RadarSeriesDTO> series;

    @Data
    public static class RadarSeriesDTO {
        private String name;
        private List<BigDecimal> values;
    }
}
