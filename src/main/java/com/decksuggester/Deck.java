package com.decksuggester;

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
    private long archidektId;
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

    public Deck() {
    }

    public Deck(UserIdentity owner, long archidektId, String name, String description,
                String sourceUrl, long rootFolderId, String rootFolderName, long folderId,
                String folderName, Instant importedAt, List<DeckCard> cards) {
        this.id = deckId(owner.ownerId(), archidektId);
        this.ownerId = owner.ownerId();
        this.libraryId = owner.libraryId();
        this.archidektId = archidektId;
        this.name = name;
        this.description = description;
        this.sourceUrl = sourceUrl;
        this.rootFolderId = rootFolderId;
        this.rootFolderName = rootFolderName;
        this.folderId = folderId;
        this.folderName = folderName;
        this.importedAt = importedAt;
        this.cards = cards == null ? List.of() : List.copyOf(cards);
        this.colorIdentity = computeColorIdentity(this.cards);
    }

    /**
     * Test-only convenience constructor for units that exercise deck logic
     * without needing a real authenticated owner.
     */
    Deck(long archidektId, String name, String description, String sourceUrl,
         long rootFolderId, String rootFolderName, long folderId, String folderName,
         Instant importedAt, List<DeckCard> cards) {
        this(new UserIdentity("test-owner", "test-owner", "test-library"), archidektId, name,
                description, sourceUrl, rootFolderId, rootFolderName, folderId, folderName,
                importedAt, cards);
    }

    static String deckId(String ownerId, long archidektId) {
        return UUID.nameUUIDFromBytes((ownerId + ":" + archidektId)
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

    public long getArchidektId() {
        return archidektId;
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

    public List<String> getColorIdentity() {
        // Older imported documents predate this field. Derive a safe fallback
        // so they are not incorrectly presented as colorless after upgrade.
        return colorIdentity == null ? computeColorIdentity(getCards()) : colorIdentity;
    }

    public List<DeckCard> getCards() {
        return cards == null ? List.of() : cards;
    }

    /**
     * A deck's color identity is the identity of its commander(s) when one is
     * designated; otherwise it falls back to the identity of every included
     * card, which is the best available signal for non-Commander decks.
     */
    static List<String> computeColorIdentity(List<DeckCard> cards) {
        if (cards == null) {
            return List.of();
        }
        List<DeckCard> included = cards.stream().filter(DeckCard::includedInDeck).toList();
        List<DeckCard> commanders = included.stream()
                .filter(card -> card.categories().stream()
                        .anyMatch(category -> category.toLowerCase(Locale.ROOT)
                                .contains("commander")))
                .toList();
        List<DeckCard> identitySource = commanders.isEmpty() ? included : commanders;
        return identitySource.stream().flatMap(card -> card.colorIdentity().stream())
                .distinct().toList();
    }
}
