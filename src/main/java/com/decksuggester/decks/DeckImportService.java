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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DeckImportService {

    private static final Pattern FOLDER_PATH = Pattern.compile("(?:^|/)folders/(\\d+)(?:/|$)");
    private static final Pattern DECKLIST_QUANTITY = Pattern.compile("^\\s*(\\d+)\\s*[*x×]?\\s+(.*)$");

    private final ArchidektGateway archidekt;
    private final MoxfieldGateway moxfield;
    private final CardRepository cardRepository;
    private final DeckRepository deckRepository;
    private final DeckHistoryService historyService;

    public DeckImportService(ArchidektGateway archidekt, MoxfieldGateway moxfield,
                             CardRepository cardRepository, DeckRepository deckRepository,
                             DeckHistoryService historyService) {
        this.archidekt = archidekt;
        this.moxfield = moxfield;
        this.cardRepository = cardRepository;
        this.deckRepository = deckRepository;
        this.historyService = historyService;
    }

    /** Dispatches a pasted URL to the right importer based on its host. */
    public ImportResult importByUrl(String url, UserIdentity owner) {
        if (url == null || url.trim().isBlank()) {
            throw new InvalidRequestException("A URL is required");
        }
        String host = hostOf(url.trim());
        if (host == null) {
            throw new InvalidRequestException("A valid URL is required");
        }
        if (host.equals("moxfield.com") || host.endsWith(".moxfield.com")) {
            return importMoxfieldDeck(url.trim(), owner);
        }
        // Default to the Archidekt folder importer for any other URL.
        return importArchidektFolder(url.trim(), owner);
    }

    public ImportResult importArchidektFolder(String folderUrl, UserIdentity owner) {
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
            parsedDecks.add(parseArchidektDeck(deck, reference));
        }

        CardLookups lookups = loadScryfallCards(parsedDecks);
        Instant importedAt = Instant.now();
        String rootFolderName = folderNames.getOrDefault(rootFolderId, "Folder " + rootFolderId);
        List<Deck> decks = parsedDecks.stream()
                .map(parsed -> toArchidektDeck(owner, parsed, rootFolderId, rootFolderName,
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

    public ImportResult importMoxfieldDeck(String deckUrl, UserIdentity owner) {
        String publicId = MoxfieldClient.extractPublicId(deckUrl);
        JsonNode deck = moxfield.fetchDeck(publicId);
        if (deck.isMissingNode() || deck.isNull()) {
            throw new MoxfieldException("Moxfield returned an empty deck for " + publicId);
        }
        ParsedDeck parsed = parseMoxfieldDeck(deck, publicId);
        CardLookups lookups = loadScryfallCards(List.of(parsed));
        Instant importedAt = Instant.now();
        Deck deckEntity = toMoxfieldDeck(owner, parsed, importedAt, lookups);
        deckRepository.save(deckEntity);
        historyService.record(owner, deckEntity, DeckHistory.Action.IMPORT, null, null,
                deckEntity.getCards().size());
        int totalCards = deckEntity.getCards().stream().mapToInt(DeckCard::quantity).sum()
                + deckEntity.getSignatureCards().stream().mapToInt(DeckCard::quantity).sum();
        int unmatchedCards = (int) deckEntity.getCards().stream()
                .filter(card -> !card.matchedScryfallCard()).count();
        return new ImportResult(0, 1, 1, totalCards, unmatchedCards,
                List.of(deckEntity.getName()));
    }

    /** Creates a {@link DeckSource#MANUAL} deck from a pasted card list plus a signature card. */
    public Deck importManualDeck(ManualImportRequest request, UserIdentity owner) {
        if (request == null) {
            throw new InvalidRequestException("A deck name and card list are required");
        }
        if (request.name() == null || request.name().trim().isBlank()) {
            throw new InvalidRequestException("A deck name is required");
        }
        if (request.signatureCardName() == null || request.signatureCardName().trim().isBlank()) {
            throw new InvalidRequestException("A signature card (commander) name is required");
        }
        String signatureName = request.signatureCardName().trim();
        Card signatureCard = cardRepository.findFirstByNameIgnoreCase(signatureName)
                .orElseThrow(() -> new InvalidRequestException("\"" + signatureName
                        + "\" is not a known Magic card"));

        List<RawCard> rawCards = parseDecklist(request.cardList());
        // The signature card is stored separately; drop it from the main list if
        // the user also pasted it so it is not double-counted.
        rawCards = rawCards.stream()
                .filter(raw -> !raw.name.trim().equalsIgnoreCase(signatureName))
                .toList();

        Set<String> names = new LinkedHashSet<>();
        for (RawCard raw : rawCards) {
            names.add(raw.name.trim());
        }
        Map<String, Card> byName = new LinkedHashMap<>();
        if (!names.isEmpty()) {
            cardRepository.findByNameInIgnoreCase(names)
                    .forEach(card -> byName.putIfAbsent(normalizeCardName(card.getName()), card));
        }

        DeckCard signature = deckCardFromCard(signatureCard, 1, List.of("Commander"), true);
        List<DeckCard> cards = new ArrayList<>();
        for (RawCard raw : rawCards) {
            Card card = byName.get(normalizeCardName(raw.name));
            if (card != null) {
                cards.add(deckCardFromCard(card, raw.quantity, List.of("Mainboard"), true));
            } else {
                cards.add(new DeckCard(null, null, raw.name, 0, List.of(), List.of(),
                        null, null, raw.quantity, List.of("Mainboard"), true, false,
                        null, null, null, null, null));
            }
        }

        long externalId = stableLong("manual:" + UUID.randomUUID());
        String sourceUrl = null;
        Deck deck = new Deck(owner, externalId, request.name().trim(),
                request.description() == null ? "" : request.description().trim(), sourceUrl,
                0L, "My Decks", 0L, "My Decks", Instant.now(), cards, DeckSource.MANUAL,
                List.of(signature), request.folderId());
        Deck saved = deckRepository.save(deck);
        historyService.record(owner, saved, DeckHistory.Action.IMPORT, null, null,
                saved.getCards().size());
        return saved;
    }

    static long extractFolderId(String folderUrl) {
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

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException exception) {
            return null;
        }
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

    private ParsedDeck parseArchidektDeck(JsonNode deck, DeckReference reference) {
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
                reference.folderName(), null, cards);
    }

    private ParsedDeck parseMoxfieldDeck(JsonNode deck, String publicId) {
        List<ParsedCard> cards = new ArrayList<>();
        List<ParsedCard> signature = new ArrayList<>();
        JsonNode boards = deck.path("boards");
        addMoxfieldCards(boards.path("commanders"), "Commander", true, signature);
        addMoxfieldCards(boards.path("signatureSpells"), "Signature Spell", true, signature);
        addMoxfieldCards(boards.path("mainboard"), "Mainboard", true, cards);
        addMoxfieldCards(boards.path("maybeboard"), "Maybeboard", false, cards);
        addMoxfieldCards(boards.path("sideboard"), "Sideboard", false, cards);

        long deckId = stableLong("moxfield:" + publicId);
        String name = text(deck, "name", "Moxfield Deck " + publicId);
        String sourceUrl = text(deck, "publicUrl", "https://moxfield.com/decks/" + publicId);
        return new ParsedDeck(deckId, name, text(deck, "description", ""), 0L,
                "Moxfield", sourceUrl, cards, signature);
    }

    private void addMoxfieldCards(JsonNode board, String category, boolean included,
                                  List<ParsedCard> target) {
        JsonNode cards = board.path("cards");
        cards.properties().forEach(entry -> {
            JsonNode entryNode = entry.getValue();
            JsonNode card = entryNode.path("card");
            int quantity = Math.max(1, entryNode.path("quantity").asInt(1));
            String scryfallId = text(card, "scryfall_id", null);
            String oracleId = text(card, "oracleId", null);
            String name = text(card, "name", "Unknown card");
            double cmc = card.path("cmc").asDouble(0);
            List<String> colors = normalizeColors(strings(card.path("colors")));
            List<String> colorIdentity = normalizeColors(strings(card.path("color_identity")));
            String typeLine = text(card, "type_line", null);
            String power = text(card, "power", null);
            String toughness = text(card, "toughness", null);
            String releasedAt = text(card, "released_at", null);
            String layout = text(card, "layout", null);
            target.add(new ParsedCard(scryfallId, oracleId, name, cmc, colors, colorIdentity,
                    typeLine, quantity, List.of(category), included, power, toughness,
                    releasedAt, layout));
        });
    }

    private CardLookups loadScryfallCards(List<ParsedDeck> decks) {
        Set<String> scryfallIds = new HashSet<>();
        Set<String> oracleIds = new HashSet<>();
        decks.stream().flatMap(deck -> deck.allCards().stream()).forEach(card -> {
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
                    .forEach(card -> byScryfallId.put(card.getScryfallId(), card));
        }
        if (!oracleIds.isEmpty()) {
            cardRepository.findByOracleIdIn(oracleIds)
                    .forEach(card -> byOracleId.putIfAbsent(card.getOracleId(), card));
        }
        return new CardLookups(byScryfallId, byOracleId);
    }

    private Deck toArchidektDeck(UserIdentity owner, ParsedDeck parsed, long rootFolderId,
                                 String rootFolderName, Instant importedAt, CardLookups lookups) {
        List<DeckCard> signature = new ArrayList<>();
        List<DeckCard> cards = new ArrayList<>();
        for (ParsedCard raw : parsed.cards()) {
            DeckCard deckCard = toDeckCard(raw, lookups);
            if (isCommander(raw)) {
                signature.add(deckCard);
            } else {
                cards.add(deckCard);
            }
        }
        String slug = ArchidektClient.slugify(parsed.name()).replace('_', '-');
        return new Deck(owner, parsed.id(), parsed.name(), parsed.description(),
                "https://archidekt.com/decks/" + parsed.id() + "/" + slug,
                rootFolderId, rootFolderName, parsed.folderId(), parsed.folderName(),
                importedAt, cards, DeckSource.ARCHIDEKT, signature, null);
    }

    private Deck toMoxfieldDeck(UserIdentity owner, ParsedDeck parsed, Instant importedAt,
                                CardLookups lookups) {
        List<DeckCard> signature = parsed.signature().stream()
                .map(raw -> toDeckCard(raw, lookups)).toList();
        List<DeckCard> cards = parsed.cards().stream()
                .map(raw -> toDeckCard(raw, lookups)).toList();
        return new Deck(owner, parsed.id(), parsed.name(), parsed.description(),
                parsed.sourceUrl(),
                0L, "Moxfield", 0L, "Moxfield", importedAt, cards, DeckSource.MOXFIELD,
                signature, null);
    }

    private DeckCard toDeckCard(ParsedCard raw, CardLookups lookups) {
        Card card = lookups.byScryfallId().get(raw.scryfallId());
        if (card == null && raw.oracleId() != null) {
            card = lookups.byOracleId().get(raw.oracleId());
        }
        if (card == null) {
            return new DeckCard(raw.scryfallId(), raw.oracleId(), raw.name(),
                    raw.manaValue(), raw.colors(), raw.colorIdentity(), raw.typeLine(),
                    null, raw.quantity(), raw.categories(), raw.includedInDeck(), false,
                    raw.power(), raw.toughness(), raw.releasedAt(), null, null);
        }
        return deckCardFromCard(card, raw.quantity(), raw.categories(), raw.includedInDeck());
    }

    private static DeckCard deckCardFromCard(Card card, int quantity, List<String> categories,
                                             boolean included) {
        return new DeckCard(card.getScryfallId(), card.getOracleId(), card.getName(),
                card.getCmc() == null ? 0.0 : card.getCmc(),
                normalizeColors(card.getColors()),
                normalizeColors(card.getColorIdentity()),
                card.getTypeLine(),
                card.getImageUrl(), Math.max(1, quantity),
                categories == null ? List.of() : List.copyOf(categories),
                included, true,
                card.getPower(), card.getToughness(), card.getReleasedAt(),
                card.getBackImageUrl(), card.getLayout());
    }

    private static boolean isCommander(ParsedCard card) {
        return card.categories().stream()
                .anyMatch(category -> category.toLowerCase(Locale.ROOT).contains("commander"));
    }

    private static List<RawCard> parseDecklist(String decklist) {
        if (decklist == null || decklist.isBlank()) {
            return List.of();
        }
        List<RawCard> cards = new ArrayList<>();
        for (String line : decklist.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            Matcher matcher = DECKLIST_QUANTITY.matcher(trimmed);
            int quantity = 1;
            String name = trimmed;
            if (matcher.matches()) {
                quantity = Math.max(1, Integer.parseInt(matcher.group(1)));
                name = matcher.group(2).trim();
            }
            if (!name.isEmpty()) {
                cards.add(new RawCard(name, quantity));
            }
        }
        return cards;
    }

    /** Derives a stable 64-bit id from a string key, for non-Archidekt decks' {@code sourceId}. */
    static long stableLong(String key) {
        byte[] bytes = UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long hash = 0;
        for (byte b : bytes) {
            hash = 31 * hash + b;
        }
        return hash;
    }

    private static String archidektTypeLine(JsonNode card) {
        List<String> left = new ArrayList<>();
        left.addAll(strings(card.path("superTypes")));
        left.addAll(strings(card.path("types")));
        List<String> subtypes = strings(card.path("subTypes"));
        return String.join(" ", left) + (subtypes.isEmpty()
                ? "" : " \u2014 " + String.join(" ", subtypes));
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(value -> values.add(value.asString()));
        }
        return values;
    }

    private static String normalizeCardName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> normalizeColors(Collection<String> colors) {
        if (colors == null) {
            return List.of();
        }
        return colors.stream().map(color -> CardColors.NAMES.getOrDefault(
                color == null ? "" : color.toUpperCase(Locale.ROOT), color)).toList();
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asString(fallback);
    }

    public record ImportResult(long rootFolderId, int foldersVisited, int decksImported,
                               int cardsImported, int unmatchedCards, List<String> deckNames) {
    }

    public record ManualImportRequest(String name, String description, String signatureCardName,
                                      String cardList, String folderId) {
    }

    private record FolderReference(long id, String name) {
    }

    private record DeckReference(long id, String name, long folderId, String folderName) {
    }

    /** Parsed deck payload shared by every importer: {@code signature} plus the rest of the deck. */
    private record ParsedDeck(long id, String name, String description, long folderId,
                              String folderName, String sourceUrl,
                              List<ParsedCard> cards,
                              List<ParsedCard> signature) {
        ParsedDeck(long id, String name, String description, long folderId, String folderName,
                   String sourceUrl, List<ParsedCard> cards) {
            this(id, name, description, folderId, folderName, sourceUrl, cards, List.of());
        }

        List<ParsedCard> allCards() {
            List<ParsedCard> all = new ArrayList<>(cards);
            all.addAll(signature);
            return all;
        }
    }

    private record ParsedCard(String scryfallId, String oracleId, String name,
                              double manaValue, List<String> colors,
                              List<String> colorIdentity, String typeLine, int quantity,
                              List<String> categories, boolean includedInDeck,
                              String power, String toughness, String releasedAt,
                              String layout) {
        ParsedCard(String scryfallId, String oracleId, String name, double manaValue,
                   List<String> colors, List<String> colorIdentity, String typeLine,
                   int quantity, List<String> categories, boolean includedInDeck) {
            this(scryfallId, oracleId, name, manaValue, colors, colorIdentity, typeLine,
                    quantity, categories, includedInDeck, null, null, null, null);
        }
    }

    private record RawCard(String name, int quantity) {
    }

    private record CardLookups(Map<String, Card> byScryfallId,
                               Map<String, Card> byOracleId) {
    }
}
