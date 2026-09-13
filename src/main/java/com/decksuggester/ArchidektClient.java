package com.decksuggester;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class ArchidektClient implements ArchidektGateway {

    private static final Logger log = LoggerFactory.getLogger(ArchidektClient.class);
    private static final Pattern NON_SLUG_CHARACTERS = Pattern.compile("[^a-z0-9]+");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String buildId;
    private final long minimumIntervalMillis;
    private final long rateLimitWaitMillis;
    private final int maxRateLimitRetries;
    private final Path fixtureDirectory;
    private long lastRequestNanos;

    @Autowired
    public ArchidektClient(
            ObjectMapper objectMapper,
            @Value("${archidekt.base-url:https://archidekt.com}") String baseUrl,
            @Value("${archidekt.build-id:-PgKf9BovkAmhdT46ZzH2}") String buildId,
            @Value("${archidekt.minimum-request-interval-ms:100}") long minimumIntervalMillis,
            @Value("${archidekt.rate-limit-wait-ms:5000}") long rateLimitWaitMillis,
            @Value("${archidekt.max-rate-limit-retries:3}") int maxRateLimitRetries,
            @Value("${archidekt.fixture-directory:}") String fixtureDirectory) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                baseUrl, buildId, minimumIntervalMillis, rateLimitWaitMillis,
                maxRateLimitRetries, fixtureDirectory);
    }

    ArchidektClient(ObjectMapper objectMapper, HttpClient httpClient, String baseUrl,
                     String buildId, long minimumIntervalMillis, long rateLimitWaitMillis,
                     int maxRateLimitRetries, String fixtureDirectory) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.buildId = buildId;
        this.minimumIntervalMillis = Math.max(100, minimumIntervalMillis);
        this.rateLimitWaitMillis = Math.max(5000, rateLimitWaitMillis);
        this.maxRateLimitRetries = Math.max(1, maxRateLimitRetries);
        this.fixtureDirectory = fixtureDirectory == null || fixtureDirectory.isBlank()
                ? null : Path.of(fixtureDirectory).toAbsolutePath().normalize();
    }

    @Override
    public JsonNode fetchFolder(long folderId) {
        Optional<JsonNode> fixture = readFixture(List.of("folder-" + folderId + ".json"));
        return fixture.orElseGet(() -> fetchJson(baseUrl + "/_next/data/" + buildId
                + "/folders/" + folderId + ".json?folderId=" + folderId));
    }

    @Override
    public JsonNode fetchDeck(long deckId, String deckName) {
        String slug = slugify(deckName);
        Optional<JsonNode> fixture = readFixture(List.of(
                slug + ".json",
                slug.replace('_', '-') + ".json",
                "deck-" + deckId + ".json"));
        return fixture.orElseGet(() -> fetchJson(baseUrl + "/_next/data/" + buildId
                + "/decks/" + deckId + "/" + slug + ".json"));
    }

    static String slugify(String name) {
        String normalized = Normalizer.normalize(name == null ? "deck" : name,
                        Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String slug = NON_SLUG_CHARACTERS.matcher(normalized).replaceAll("_")
                .replaceAll("^_+|_+$", "");
        return slug.isBlank() ? "deck" : slug;
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
                throw new ArchidektException("Could not read fixture " + fixture, exception);
            }
        }
        return Optional.empty();
    }

    private JsonNode fetchJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "DeckSuggester/1.0")
                .GET()
                .build();

        for (int attempt = 0; attempt <= maxRateLimitRetries; attempt++) {
            waitForRequestSlot();
            HttpResponse<String> response;
            try {
                response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new ArchidektException("Failed to fetch Archidekt data", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ArchidektException("Interrupted while fetching Archidekt data", exception);
            }

            if (response.statusCode() == 429) {
                if (attempt == maxRateLimitRetries) {
                    log.warn("Archidekt returned HTTP 429 for {}; retry limit exhausted after {} attempts",
                            url, maxRateLimitRetries + 1);
                    break;
                }
                log.warn("Archidekt returned HTTP 429 for {}; waiting {} ms (request attempt {}/{})",
                        url, rateLimitWaitMillis, attempt + 1, maxRateLimitRetries + 1);
                sleep(rateLimitWaitMillis);
                continue;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ArchidektException("Archidekt returned HTTP "
                        + response.statusCode() + " for " + url);
            }
            try {
                return objectMapper.readTree(response.body());
            } catch (Exception exception) {
                throw new ArchidektException("Archidekt returned invalid JSON for " + url,
                        exception);
            }
        }
        throw new ArchidektException("Archidekt continued returning HTTP 429 for " + url);
    }

    private synchronized void waitForRequestSlot() {
        long intervalNanos = TimeUnit.MILLISECONDS.toNanos(minimumIntervalMillis);
        long now = System.nanoTime();
        long remainingNanos = lastRequestNanos + intervalNanos - now;
        if (lastRequestNanos != 0 && remainingNanos > 0) {
            long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            int nanos = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(millis));
            try {
                Thread.sleep(millis, nanos);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ArchidektException("Interrupted while applying the Archidekt rate limit",
                        exception);
            }
        }
        lastRequestNanos = System.nanoTime();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ArchidektException("Interrupted while waiting to retry Archidekt", exception);
        }
    }
}
