package com.decksuggester;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class CardController {

    private final CardRepository cardRepository;
    private final ScryfallImageService imageService;

    public CardController(CardRepository cardRepository, ScryfallImageService imageService) {
        this.cardRepository = cardRepository;
        this.imageService = imageService;
    }

    @GetMapping("/cards/count")
    public long count() {
        return cardRepository.count();
    }

    @GetMapping("/cards/{scryfallId}/image")
    public ResponseEntity<byte[]> image(@PathVariable String scryfallId) {
        ScryfallImageService.ProxiedImage image = imageService.fetch(scryfallId);
        return ResponseEntity.ok()
                .contentType(image.contentType())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(image.bytes());
    }

}
