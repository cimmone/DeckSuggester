package com.decksuggester;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataController {

    private final DeckStatisticsService statisticsService;

    public DataController(DeckStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping({"/data", "/data/"})
    public DeckStatisticsService.DataStatistics data() {
        return statisticsService.statistics();
    }
}
