package searchengine.dto.statistics;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class StatisticsResponse {

    private Boolean result;
    private StatisticsData statistics;
    private Long count;
    private List<SearchData> data;
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

    public StatisticsResponse(Boolean result, Long count, List<SearchData> data) {
        this.result = result;
        this.count = count;
        this.data = data;
    }
}
