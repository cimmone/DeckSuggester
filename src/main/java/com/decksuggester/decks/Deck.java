package com.decksuggester.decks;

import com.decksuggester.auth.UserIdentity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Document(collection = "decks")
public class Deck {

    @Id
    private String id;
    private String ownerId;
    private String libraryId;
    private long sourceId;
    private String name;
    private String description;
    private String sourceUrl;
    private long rootFolderId;
    private String rootFolderName;
    private long folderId;
    private String folderName;
    private Instant importedAt;
    private List<String> colorIdentity;
    private List<DeckCard> cards;

    /** Origin of the deck. */
    private DeckSource source;

    /** The signature card(s)/commander(s) the deck is built around. */
    private List<DeckCard> signatureCards;

    /** Optional {@link Folder} id the deck lives in; null means the library root. */
    private String folderRef;

    public Deck() {
    }

    public Deck(UserIdentity owner, long sourceId, String name, String description,
                String sourceUrl, long rootFolderId, String rootFolderName, long folderId,
                String folderName, Instant importedAt, List<DeckCard> cards) {
        this(owner, sourceId, name, description, sourceUrl, rootFolderId, rootFolderName,
                folderId, folderName, importedAt, cards, DeckSource.ARCHIDEKT, List.of(), null);
    }

    public Deck(UserIdentity owner, long sourceId, String name, String description,
                String sourceUrl, long rootFolderId, String rootFolderName, long folderId,
                String folderName, Instant importedAt, List<DeckCard> cards,
                DeckSource source, List<DeckCard> signatureCards, String folderRef) {
        this.id = deckId(owner.ownerId(), sourceId);
        this.ownerId = owner.ownerId();
        this.libraryId = owner.libraryId();
        this.sourceId = sourceId;
        this.name = name;
        this.description = description;
        this.sourceUrl = sourceUrl;
        this.rootFolderId = rootFolderId;
        this.rootFolderName = rootFolderName;
        this.folderId = folderId;
        this.folderName = folderName;
        this.importedAt = importedAt;
        this.cards = cards == null ? List.of() : List.copyOf(cards);
        this.signatureCards = signatureCards == null ? List.of() : List.copyOf(signatureCards);
        this.colorIdentity = computeColorIdentity(this.signatureCards, this.cards);
        this.source = source;
        this.folderRef = folderRef;
    }

    /** Test-only convenience constructor that doesn't need a real authenticated owner. */
    Deck(long sourceId, String name, String description, String sourceUrl,
         long rootFolderId, String rootFolderName, long folderId, String folderName,
         Instant importedAt, List<DeckCard> cards) {
        this(new UserIdentity("test-owner", "test-owner", "test-library"), sourceId, name,
                description, sourceUrl, rootFolderId, rootFolderName, folderId, folderName,
                importedAt, cards);
    }

    static String deckId(String ownerId, long sourceId) {
        return UUID.nameUUIDFromBytes((ownerId + ":" + sourceId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    public String getId() {
        return id;
    }

    @JsonIgnore
    public String getOwnerId() {
        return ownerId;
    }

    @JsonIgnore
    public String getLibraryId() {
        return libraryId;
    }

    public long getSourceId() {
        return sourceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public long getRootFolderId() {
        return rootFolderId;
    }

    public String getRootFolderName() {
        return rootFolderName;
    }

    public long getFolderId() {
        return folderId;
    }

    public String getFolderName() {
        return folderName;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public DeckSource getSource() {
        return source;
    }

    public void setSource(DeckSource source) {
        this.source = source;
    }

    public List<DeckCard> getSignatureCards() {
        return signatureCards == null ? List.of() : signatureCards;
    }

    public void setSignatureCards(List<DeckCard> signatureCards) {
        this.signatureCards = signatureCards == null ? List.of() : List.copyOf(signatureCards);
        this.colorIdentity = computeColorIdentity(this.signatureCards, this.cards);
    }

    public String getFolderRef() {
        return folderRef;
    }

    public void setFolderRef(String folderRef) {
        this.folderRef = folderRef == null || folderRef.isBlank() ? null : folderRef;
    }

    public List<String> getColorIdentity() {
        return colorIdentity;
    }

    public List<DeckCard> getCards() {
        return cards == null ? List.of() : cards;
    }

    /** Replaces the deck's card list and recomputes the cached color identity. */
    public void setCards(List<DeckCard> cards) {
        this.cards = cards == null ? List.of() : List.copyOf(cards);
        this.colorIdentity = computeColorIdentity(this.signatureCards, this.cards);
    }

    /** A deck's color identity is its signature card(s)', or every card's if none is set. */
    static List<String> computeColorIdentity(List<DeckCard> cards) {
        return computeColorIdentity(null, cards);
    }

    static List<String> computeColorIdentity(List<DeckCard> signatureCards, List<DeckCard> cards) {
        if (cards == null) {
            return List.of();
        }
        List<DeckCard> includedSignature = signatureCards == null ? List.of()
                : signatureCards.stream().filter(DeckCard::includedInDeck).toList();
        List<DeckCard> included = cards.stream().filter(DeckCard::includedInDeck).toList();
        List<DeckCard> commanders = includedSignature.isEmpty()
                ? included.stream()
                        .filter(card -> card.categories().stream()
                                .anyMatch(category -> category.toLowerCase(Locale.ROOT)
                                        .contains("commander")))
                        .toList()
                : includedSignature;
        List<DeckCard> identitySource = commanders.isEmpty() ? included : commanders;
        return identitySource.stream().flatMap(card -> card.colorIdentity().stream())
                .distinct().toList();
    }
}
