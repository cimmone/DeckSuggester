package com.decksuggester.decks;

import com.decksuggester.InvalidRequestException;
import com.decksuggester.auth.CurrentUserService;
import com.decksuggester.auth.UserIdentity;
import com.decksuggester.spellbook.RecommendationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/decks")
public class DeckController {

    private final DeckRepository deckRepository;
    private final DeckImportService importService;
    private final DeckEditService editService;
    private final DeckHistoryService historyService;
    private final RecommendationService recommendationService;
    private final FolderService folderService;
    private final CurrentUserService currentUser;

    public DeckController(DeckRepository deckRepository, DeckImportService importService,
                          DeckEditService editService, DeckHistoryService historyService,
                          RecommendationService recommendationService,
                          FolderService folderService, CurrentUserService currentUser) {
        this.deckRepository = deckRepository;
        this.importService = importService;
        this.editService = editService;
        this.historyService = historyService;
        this.recommendationService = recommendationService;
        this.folderService = folderService;
        this.currentUser = currentUser;
    }

    @GetMapping({ "", "/" })
    public List<Deck> decks() {
        UserIdentity owner = currentUser.require();
        return deckRepository.findAllByOwnerIdAndLibraryIdOrderByNameAsc(
                owner.ownerId(), owner.libraryId());
    }

    /** Imports a deck (or Archidekt folder of decks) from a pasted URL. */
    @PostMapping({ "", "/" })
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public DeckImportService.ImportResult importDeck(@RequestBody ImportRequest request) {
        if (request == null || request.url() == null || request.url().isBlank()) {
            throw new InvalidRequestException("A JSON body containing a url is required");
        }
        return importService.importByUrl(request.url(), currentUser.require());
    }

    /** Creates a deck from a pasted card list plus a required signature card. */
    @PostMapping({ "/manual", "/manual/" })
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public Deck importManualDeck(@RequestBody DeckImportService.ManualImportRequest request) {
        return importService.importManualDeck(request, currentUser.require());
    }

    @PutMapping("/{id}")
    public Deck update(@PathVariable String id, @RequestBody DeckUpdateRequest request) {
        UserIdentity owner = currentUser.require();
        Deck deck = deckRepository.findByIdAndOwnerIdAndLibraryId(
                        id, owner.ownerId(), owner.libraryId())
                .orElseThrow(() -> new DeckNotFoundException(id));
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new InvalidRequestException("Deck name cannot be blank");
        }
        deck.setName(request.name().trim());
        if (request.description() != null) {
            deck.setDescription(request.description());
        }
        return deckRepository.save(deck);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        UserIdentity owner = currentUser.require();
        if (deckRepository.deleteByIdAndOwnerIdAndLibraryId(
                id, owner.ownerId(), owner.libraryId()) == 0) {
            throw new DeckNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping({ "", "/" })
    public ResponseEntity<Void> deleteAll() {
        UserIdentity owner = currentUser.require();
        deckRepository.deleteAllByOwnerIdAndLibraryId(owner.ownerId(), owner.libraryId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public Deck deck(@PathVariable String id) {
        UserIdentity owner = currentUser.require();
        return deckRepository.findByIdAndOwnerIdAndLibraryId(
                        id, owner.ownerId(), owner.libraryId())
                .orElseThrow(() -> new DeckNotFoundException(id));
    }

    @GetMapping("/card-search")
    public List<DeckEditService.CardSuggestion> searchCards(
            @org.springframework.web.bind.annotation.RequestParam("q") String query) {
        currentUser.require();
        return editService.searchCards(query);
    }

    @PostMapping("/{id}/cards")
    public Deck addCard(@PathVariable String id, @RequestBody CardMutationRequest request) {
        UserIdentity owner = currentUser.require();
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new InvalidRequestException("A card name is required");
        }
        return editService.addCard(owner, id, request.name());
    }

    @DeleteMapping("/{id}/cards")
    public Deck removeCard(@PathVariable String id, @RequestBody CardMutationRequest request) {
        UserIdentity owner = currentUser.require();
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new InvalidRequestException("A card identifier is required");
        }
        return editService.removeCard(owner, id, request.name());
    }

    /** Moves a deck into a folder, or to the library root when {@code folderId} is blank. */
    @PutMapping("/{id}/folder")
    public Deck moveDeckToFolder(@PathVariable String id,
                                 @RequestBody MoveDeckFolderRequest request) {
        return folderService.moveDeck(currentUser.require(), id,
                request == null ? null : request.folderId());
    }

    @GetMapping("/{id}/recommendations")
    public List<RecommendationService.Recommendation> recommendations(
            @PathVariable String id,
            @org.springframework.web.bind.annotation.RequestParam(
                    value = "filter", defaultValue = "legal") String filter,
            @org.springframework.web.bind.annotation.RequestParam(
                    value = "format", defaultValue = "commander") String format) {
        UserIdentity owner = currentUser.require();
        Deck deck = deckRepository.findByIdAndOwnerIdAndLibraryId(
                        id, owner.ownerId(), owner.libraryId())
                .orElseThrow(() -> new DeckNotFoundException(id));
        RecommendationService.FilterMode mode = RecommendationService.FilterMode.fromQuery(filter);
        return recommendationService.recommend(deck, 50, mode, format);
    }

    @GetMapping("/history")
    public List<DeckHistory> history() {
        UserIdentity owner = currentUser.require();
        return historyService.recentForOwner(owner);
    }

    public record CardMutationRequest(String name) {
    }

    public record ImportRequest(String url) {
    }

    public record MoveDeckFolderRequest(String folderId) {
    }

    public record DeckUpdateRequest(String name, String description) {
    }
}
