package com.decksuggester.decks;

import com.decksuggester.InvalidRequestException;
import com.decksuggester.auth.UserIdentity;
import com.decksuggester.cards.Card;
import com.decksuggester.cards.CardRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DeckImportService {

    private static final Pattern FOLDER_PATH = Pattern.compile("(?:^|/)folders/(\\d+)(?:/|$)");
    private static final Map<String, String> COLOR_NAMES = Map.of(
            "W", "White", "U", "Blue", "B", "Black", "R", "Red", "G", "Green");

    private final ArchidektGateway archidekt;
    private final CardRepository cardRepository;
    private final DeckRepository deckRepository;
    private final DeckHistoryService historyService;

    public DeckImportService(ArchidektGateway archidekt, CardRepository cardRepository,
                             DeckRepository deckRepository, DeckHistoryService historyService) {
        this.archidekt = archidekt;
        this.cardRepository = cardRepository;
        this.deckRepository = deckRepository;
        this.historyService = historyService;
    }

    public ImportResult importFolder(String folderUrl, UserIdentity owner) {
        long rootFolderId = extractFolderId(folderUrl);
        Queue<FolderReference> pendingFolders = new ArrayDeque<>();
        Set<Long> visitedFolders = new LinkedHashSet<>();
        Map<Long, String> folderNames = new LinkedHashMap<>();
        Map<Long, DeckReference> deckReferences = new LinkedHashMap<>();
        pendingFolders.add(new FolderReference(rootFolderId, null));

        while (!pendingFolders.isEmpty()) {
            FolderReference reference = pendingFolders.remove();
            if (!visitedFolders.add(reference.id())) {
                continue;
            }
            JsonNode folder = locateFolder(archidekt.fetchFolder(reference.id()));
            if (folder.isMissingNode() || folder.isNull()) {
                throw new ArchidektException("Folder " + reference.id()
                        + " was missing from the Archidekt response");
            }
            String folderName = text(folder, "name", reference.name() == null
                    ? "Folder " + reference.id() : reference.name());
            folderNames.put(reference.id(), folderName);

            for (JsonNode subfolder : folder.path("subfolders")) {
                long id = subfolder.path("id").asLong(0);
                if (id > 0 && !visitedFolders.contains(id)) {
                    pendingFolders.add(new FolderReference(id,
                            text(subfolder, "name", "Folder " + id)));
                }
            }
            for (JsonNode deck : folder.path("decks")) {
                long id = deck.path("id").asLong(0);
                if (id > 0) {
                    deckReferences.putIfAbsent(id, new DeckReference(id,
                            text(deck, "name", "Deck " + id), reference.id(), folderName));
                }
            }
        }

        List<ParsedDeck> parsedDecks = new ArrayList<>();
        for (DeckReference reference : deckReferences.values()) {
            JsonNode deck = locateDeck(archidekt.fetchDeck(reference.id(), reference.name()));
            if (deck.isMissingNode() || deck.isNull()) {
                throw new ArchidektException("Deck " + reference.id()
                        + " was missing from the Archidekt response");
            }
            parsedDecks.add(parseDeck(deck, reference));
        }

        CardLookups lookups = loadScryfallCards(parsedDecks);
        Instant importedAt = Instant.now();
        String rootFolderName = folderNames.getOrDefault(rootFolderId,
                "Folder " + rootFolderId);
        List<Deck> decks = parsedDecks.stream()
                .map(parsed -> toDeck(owner, parsed, rootFolderId, rootFolderName,
                        importedAt, lookups))
                .toList();
        deckRepository.saveAll(decks);
        decks.forEach(deck -> historyService.record(owner, deck,
                DeckHistory.Action.IMPORT, null, null, deck.getCards().size()));

        int totalCards = decks.stream().flatMap(deck -> deck.getCards().stream())
                .mapToInt(DeckCard::quantity).sum();
        int unmatchedCards = (int) decks.stream().flatMap(deck -> deck.getCards().stream())
                .filter(card -> !card.matchedScryfallCard()).count();
        return new ImportResult(rootFolderId, visitedFolders.size(), decks.size(),
                totalCards, unmatchedCards, decks.stream().map(Deck::getName).toList());
    }

    public static long extractFolderId(String folderUrl) {
        if (folderUrl == null || folderUrl.isBlank()) {
            throw new InvalidRequestException("A non-empty Archidekt folder URL is required");
        }
        URI uri;
        try {
            uri = URI.create(folderUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Invalid Archidekt folder URL");
        }
        String host = uri.getHost();
        if (host == null || !(host.equalsIgnoreCase("archidekt.com")
                || host.toLowerCase(Locale.ROOT).endsWith(".archidekt.com"))) {
            throw new InvalidRequestException("URL must point to archidekt.com");
        }
        Matcher matcher = FOLDER_PATH.matcher(uri.getPath());
        if (!matcher.find()) {
            throw new InvalidRequestException("URL must contain /folders/{folderId}");
        }
        return Long.parseLong(matcher.group(1));
    }

    private JsonNode locateFolder(JsonNode response) {
        JsonNode nested = response.at("/pageProps/redux/folders/rootFolder");
        if (!nested.isMissingNode()) {
            return nested;
        }
        nested = response.path("rootFolder");
        return nested.isMissingNode() ? response : nested;
    }

    private JsonNode locateDeck(JsonNode response) {
        JsonNode nested = response.at("/pageProps/redux/deck");
        if (!nested.isMissingNode()) {
            return nested;
        }
        nested = response.path("deck");
        return nested.isMissingNode() ? response : nested;
    }

    private ParsedDeck parseDeck(JsonNode deck, DeckReference reference) {
        Set<String> includedCategories = new HashSet<>();
        deck.path("categories").properties().forEach(entry -> {
            if (entry.getValue().path("includedInDeck").asBoolean(true)) {
                includedCategories.add(entry.getKey());
            }
        });

        List<ParsedCard> cards = new ArrayList<>();
        deck.path("cardMap").properties().forEach(entry -> {
            JsonNode card = entry.getValue();
            List<String> categories = strings(card.path("categories"));
            boolean included = categories.isEmpty() || includedCategories.isEmpty()
                    || categories.stream().anyMatch(includedCategories::contains);
            cards.add(new ParsedCard(
                    text(card, "uid", null),
                    text(card, "oracleCardUid", null),
                    text(card, "name", "Unknown card"),
                    card.path("cmc").asDouble(0),
                    normalizeColors(strings(card.path("colors"))),
                    normalizeColors(strings(card.path("colorIdentity"))),
                    archidektTypeLine(card),
                    Math.max(1, card.path("qty").asInt(1)),
                    categories,
                    included));
        });
        long deckId = deck.path("id").asLong(reference.id());
        return new ParsedDeck(deckId, text(deck, "name", reference.name()),
                text(deck, "description", ""), reference.folderId(),
                reference.folderName(), cards);
    }

    private CardLookups loadScryfallCards(List<ParsedDeck> decks) {
        Set<String> scryfallIds = new HashSet<>();
        Set<String> oracleIds = new HashSet<>();
        decks.stream().flatMap(deck -> deck.cards().stream()).forEach(card -> {
            if (card.scryfallId() != null) {
                scryfallIds.add(card.scryfallId());
            }
            if (card.oracleId() != null) {
                oracleIds.add(card.oracleId());
            }
        });
        Map<String, Card> byScryfallId = new HashMap<>();
        Map<String, Card> byOracleId = new HashMap<>();
        if (!scryfallIds.isEmpty()) {
            cardRepository.findByScryfallIdIn(scryfallIds)
                    .forEach(card -> byScryfallId.put(card.getId(), card));
        }
        if (!oracleIds.isEmpty()) {
            cardRepository.findByOracleIdIn(oracleIds)
                    .forEach(card -> byOracleId.putIfAbsent(card.getOracleId(), card));
        }
        return new CardLookups(byScryfallId, byOracleId);
    }

    private Deck toDeck(UserIdentity owner, ParsedDeck parsed, long rootFolderId,
                        String rootFolderName, Instant importedAt, CardLookups lookups) {
        List<DeckCard> cards = parsed.cards().stream().map(raw -> {
            Card card = lookups.byScryfallId().get(raw.scryfallId());
            if (card == null) {
                card = lookups.byOracleId().get(raw.oracleId());
            }
            if (card == null) {
                return new DeckCard(raw.scryfallId(), raw.oracleId(), raw.name(),
                        raw.manaValue(), raw.colors(), raw.colorIdentity(), raw.typeLine(),
                        null, raw.quantity(), raw.categories(), raw.includedInDeck(), false);
            }
            return new DeckCard(card.getId(), card.getOracleId(), card.getName(),
                    card.getCmc() == null ? raw.manaValue() : card.getCmc(),
                    normalizeColors(card.getColors()),
                    normalizeColors(card.getColorIdentity()),
                    card.getTypeLine() == null ? raw.typeLine() : card.getTypeLine(),
                    card.getImageUrl(), raw.quantity(), raw.categories(),
                    raw.includedInDeck(), true,
                    card.getPower(), card.getToughness(), card.getReleasedAt());
        }).toList();
        String slug = ArchidektClient.slugify(parsed.name()).replace('_', '-');
        return new Deck(owner, parsed.id(), parsed.name(), parsed.description(),
                "https://archidekt.com/decks/" + parsed.id() + "/" + slug,
                rootFolderId, rootFolderName, parsed.folderId(), parsed.folderName(),
                importedAt, cards);
    }

    private static String archidektTypeLine(JsonNode card) {
        List<String> left = new ArrayList<>();
        left.addAll(strings(card.path("superTypes")));
        left.addAll(strings(card.path("types")));
        List<String> subtypes = strings(card.path("subTypes"));
        return String.join(" ", left) + (subtypes.isEmpty()
                ? "" : " — " + String.join(" ", subtypes));
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(value -> values.add(value.asString()));
        }
        return values;
    }

    private static List<String> normalizeColors(Collection<String> colors) {
        return colors.stream().map(color -> COLOR_NAMES.getOrDefault(color, color)).toList();
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asString(fallback);
    }

    public record ImportResult(long rootFolderId, int foldersVisited, int decksImported,
                               int cardsImported, int unmatchedCards, List<String> deckNames) {
    }

    private record FolderReference(long id, String name) {
    }

    private record DeckReference(long id, String name, long folderId, String folderName) {
    }

    private record ParsedDeck(long id, String name, String description, long folderId,
                              String folderName, List<ParsedCard> cards) {
    }

    private record ParsedCard(String scryfallId, String oracleId, String name,
                              double manaValue, List<String> colors,
                              List<String> colorIdentity, String typeLine, int quantity,
                              List<String> categories, boolean includedInDeck) {
    }

    private record CardLookups(Map<String, Card> byScryfallId,
                               Map<String, Card> byOracleId) {
    }
}
