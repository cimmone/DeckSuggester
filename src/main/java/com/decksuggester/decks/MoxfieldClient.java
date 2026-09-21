package com.decksuggester.decks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import com.decksuggester.InvalidRequestException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches a single Moxfield deck's JSON payload from the public
 * {@code api2.moxfield.com} deck endpoint, with an offline fixture-directory
 * fallback (mirroring {@link ArchidektClient}).
 */
@Component
public class MoxfieldClient implements MoxfieldGateway {

    private static final Logger log = LoggerFactory.getLogger(MoxfieldClient.class);

    /** Extracts the public deck id from a Moxfield deck URL (or a bare id). */
    static final Pattern DECK_PATH = Pattern.compile(
            "(?:^|/)decks/([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final Path fixtureDirectory;

    @Autowired
    public MoxfieldClient(ObjectMapper objectMapper,
            @Value("${moxfield.base-url:https://api2.moxfield.com}") String baseUrl,
            @Value("${moxfield.fixture-directory:}") String fixtureDirectory) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                baseUrl, fixtureDirectory);
    }

    MoxfieldClient(ObjectMapper objectMapper, HttpClient httpClient, String baseUrl,
            String fixtureDirectory) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.baseUrl = baseUrl == null ? "https://api2.moxfield.com"
                : baseUrl.replaceAll("/+$", "");
        this.fixtureDirectory = fixtureDirectory == null || fixtureDirectory.isBlank()
                ? null : Path.of(fixtureDirectory).toAbsolutePath().normalize();
    }

    @Override
    public JsonNode fetchDeck(String publicId) {
        Optional<JsonNode> fixture = readFixture(List.of(
                "moxfield-" + publicId + ".json", "deck-" + publicId + ".json", "deck.json"));
        return fixture.orElseGet(() -> fetchJson(baseUrl + "/v3/decks/all/" + publicId));
    }

    /** Pulls the trailing deck id out of a Moxfield deck URL, rejecting any other host. */
    static String extractPublicId(String deckUrl) {
        if (deckUrl == null || deckUrl.isBlank()) {
            throw new InvalidRequestException("A Moxfield deck URL is required");
        }
        String trimmed = deckUrl.trim();
        // A bare id (no scheme/host) is accepted as a convenience.
        Matcher direct = DECK_PATH.matcher(trimmed);
        if (!direct.find()) {
            throw new InvalidRequestException(
                    "URL must contain /decks/{deckId} for a Moxfield deck");
        }
        return direct.group(1);
    }

    private JsonNode fetchJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "DeckSuggester/1.0")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new MoxfieldException("Failed to fetch Moxfield deck data", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MoxfieldException("Interrupted while fetching Moxfield deck data",
                    exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new MoxfieldException("Moxfield returned HTTP "
                    + response.statusCode() + " for " + url);
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception exception) {
            throw new MoxfieldException("Moxfield returned invalid JSON for " + url, exception);
        }
    }

    private Optional<JsonNode> readFixture(List<String> names) {
        if (fixtureDirectory == null) {
            return Optional.empty();
        }
        for (String name : names) {
            Path fixture = fixtureDirectory.resolve(name).normalize();
            if (!fixture.startsWith(fixtureDirectory) || !Files.isRegularFile(fixture)) {
                continue;
            }
            try {
                return Optional.of(objectMapper.readTree(Files.readString(fixture)));
            } catch (IOException exception) {
                throw new MoxfieldException("Could not read fixture " + fixture, exception);
            }
        }
        return Optional.empty();
    }
}
