package searchengine.dto.statistics;

import lombok.Data;
import searchengine.model.Status;

import java.time.ZonedDateTime;

@Data
public class DetailedStatisticsItem {
    private String url;
    private String name;
    private Status status;
    private ZonedDateTime statusTime;
    private String error;
    private Long pages;
    private Long lemmas;

    public DetailedStatisticsItem(String url, String name, Status status, ZonedDateTime statusTime, Long pages, Long lemmas) {
        this.url = url;
        this.name = name;
        this.status = status;
        this.statusTime = statusTime;
        this.pages = pages;
        this.lemmas = lemmas;
    }
}
