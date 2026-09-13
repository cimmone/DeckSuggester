package com.decksuggester;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "decks")
public class Deck {

    @Id
    private String id;
    private long archidektId;
    private String name;
    private String description;
    private String sourceUrl;
    private long rootFolderId;
    private String rootFolderName;
    private long folderId;
    private String folderName;
    private Instant importedAt;
    private List<DeckCard> cards;

    public Deck() {
    }

    public Deck(long archidektId, String name, String description, String sourceUrl,
                long rootFolderId, String rootFolderName, long folderId, String folderName,
                Instant importedAt, List<DeckCard> cards) {
        this.id = Long.toString(archidektId);
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
    }

    public String getId() {
        return id;
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

    public List<DeckCard> getCards() {
        return cards == null ? List.of() : cards;
    }
}
