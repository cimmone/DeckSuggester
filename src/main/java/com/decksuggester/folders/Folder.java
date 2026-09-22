package com.decksuggester.folders;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** A user-managed folder that organises decks in the library, forming a forest via {@link #parentId}. */
@Document(collection = "folders")
public class Folder {

    @Id
    private String id;
    private String ownerId;
    private String libraryId;
    private String name;
    private String parentId;
    private int order;
    private Instant createdAt;

    public Folder() {
    }

    public Folder(String ownerId, String libraryId, String name, String parentId, int order,
                  Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.ownerId = ownerId;
        this.libraryId = libraryId;
        this.name = name;
        this.parentId = parentId;
        this.order = order;
        this.createdAt = createdAt;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentId() {
        return parentId == null || parentId.isBlank() ? null : parentId;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    void setParentId(String parentId) {
        this.parentId = parentId == null || parentId.isBlank() ? null : parentId;
    }
}
