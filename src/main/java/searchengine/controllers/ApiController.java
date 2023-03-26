package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;

    public ApiController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<StatisticsResponse> startIndexing() {
        return ResponseEntity.ok(statisticsService.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<StatisticsResponse> stopIndexing() {
        return ResponseEntity.ok(statisticsService.stopIndexing());
    }
    @PostMapping("/indexPage")
    @ResponseBody
    public ResponseEntity<StatisticsResponse> indexPage(@RequestParam String url) {
        return ResponseEntity.ok(statisticsService.indexPage(url));
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/search")
    public ResponseEntity<StatisticsResponse> search(@RequestParam String query, String site, Long offset, Long limit) {
        return ResponseEntity.ok(statisticsService.search(query, site, offset, limit));
    }
}
