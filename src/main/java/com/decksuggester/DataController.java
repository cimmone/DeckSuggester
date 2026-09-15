package com.decksuggester;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DataController {

    private final DeckStatisticsService statisticsService;
    private final DeckRepository deckRepository;
    private final CurrentUserService currentUser;

    public DataController(DeckStatisticsService statisticsService, DeckRepository deckRepository,
                          CurrentUserService currentUser) {
        this.statisticsService = statisticsService;
        this.deckRepository = deckRepository;
        this.currentUser = currentUser;
    }

    @GetMapping({"/data", "/data/"})
    public DeckStatisticsService.DataStatistics data() {
        return statisticsService.statistics();
    }

    @GetMapping("/library")
    public LibraryData library() {
        UserIdentity owner = currentUser.require();
        List<Deck> decks = deckRepository.findAllByOwnerIdAndLibraryIdOrderByNameAsc(
                owner.ownerId(), owner.libraryId());
        return new LibraryData(decks, DeckStatisticsService.calculate(decks));
    }

    public record LibraryData(List<Deck> decks,
                              DeckStatisticsService.DataStatistics statistics) {
    }
}
