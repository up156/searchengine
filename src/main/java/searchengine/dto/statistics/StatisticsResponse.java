package searchengine.dto.statistics;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StatisticsResponse {

    private Boolean result;
    private StatisticsData statistics;
    private String error;

    public StatisticsResponse(Boolean result) {
        this.result = result;
    }

    public StatisticsResponse(Boolean result, String error) {
        this.result = result;
        this.error = error;
    }

    public StatisticsResponse(Boolean result, StatisticsData statistics) {
        this.result = result;
        this.statistics = statistics;
    }
}
