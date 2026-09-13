package com.decksuggester;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.mongodb.core.MongoOperations;
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
    private final MongoOperations mongoOperations;

    public DeckController(DeckRepository deckRepository, DeckImportService importService,
                          MongoOperations mongoOperations) {
        this.deckRepository = deckRepository;
        this.importService = importService;
        this.mongoOperations = mongoOperations;
    }

    @GetMapping({"", "/"})
    public List<Deck> decks() {
        return deckRepository.findAllByOrderByNameAsc();
    }

    @PostMapping({"", "/"})
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public DeckImportService.ImportResult importDecks(@RequestBody ImportRequest request) {
        if (request == null) {
            throw new InvalidRequestException("A JSON body containing a url is required");
        }
        return importService.importFolder(request.url());
    }

    @PutMapping("/{id}")
    public Deck update(@PathVariable String id, @RequestBody DeckUpdateRequest request) {
        Deck deck = deckRepository.findById(id)
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
        if (!deckRepository.existsById(id)) {
            throw new DeckNotFoundException(id);
        }
        deckRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping({"", "/"})
    public ResponseEntity<Void> deleteAll() {
        mongoOperations.dropCollection(Deck.class);
        return ResponseEntity.noContent().build();
    }

    public record ImportRequest(String url) {
    }

    public record DeckUpdateRequest(String name, String description) {
    }
}
