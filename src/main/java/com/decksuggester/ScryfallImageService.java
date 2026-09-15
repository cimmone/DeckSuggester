package com.decksuggester;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Service
public class ScryfallImageService {

    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private final CardRepository cardRepository;
    private final HttpClient httpClient;

    @Autowired
    public ScryfallImageService(CardRepository cardRepository) {
        this(cardRepository, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    ScryfallImageService(CardRepository cardRepository, HttpClient httpClient) {
        this.cardRepository = cardRepository;
        this.httpClient = httpClient;
    }

    public ProxiedImage fetch(String scryfallId) {
        Card card = cardRepository.findFirstByScryfallId(scryfallId)
                .orElseThrow(CardImageNotFoundException::new);
        URI uri = validatedScryfallUri(card.getImageUrl());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "image/*")
                .header("User-Agent", "DeckSuggester/1.0")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || response.body().length == 0 || response.body().length > MAX_IMAGE_BYTES) {
                throw new CardImageNotFoundException();
            }
            MediaType contentType = response.headers().firstValue("Content-Type")
                    .map(value -> value.split(";", 2)[0])
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith("image/"))
                    .map(MediaType::parseMediaType)
                    .orElse(MediaType.IMAGE_JPEG);
            return new ProxiedImage(response.body(), contentType);
        } catch (IOException exception) {
            throw new CardImageNotFoundException();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CardImageNotFoundException();
        }
    }

    static URI validatedScryfallUri(String imageUrl) {
        if (imageUrl == null) {
            throw new CardImageNotFoundException();
        }
        URI uri;
        try {
            uri = URI.create(imageUrl);
        } catch (IllegalArgumentException exception) {
            throw new CardImageNotFoundException();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean scryfallHost = host.equals("scryfall.io") || host.endsWith(".scryfall.io");
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !scryfallHost
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new CardImageNotFoundException();
        }
        return uri;
    }

    public record ProxiedImage(byte[] bytes, MediaType contentType) {
    }
}
