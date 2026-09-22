package com.decksuggester.folders;

public class FolderNotFoundException extends RuntimeException {

    public FolderNotFoundException(String id) {
        super("Folder " + id + " was not found");
    }
}
