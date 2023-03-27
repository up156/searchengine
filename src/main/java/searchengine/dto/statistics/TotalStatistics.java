package searchengine.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TotalStatistics {
    private Long sites;
    private Long pages;
    private Long lemmas;
    private Boolean indexing;
}
