package searchengine.services;

import searchengine.dto.statistics.StatisticsResponse;

public interface StatisticsService {
    StatisticsResponse getStatistics();

    StatisticsResponse startIndexing();

    StatisticsResponse stopIndexing();

    StatisticsResponse indexPage(String url);

    StatisticsResponse search(String query, String site, Long offset, Long limit);
}
