package com.decksuggester.folders;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends MongoRepository<Folder, String> {

    List<Folder> findAllByOwnerIdAndLibraryId(String ownerId, String libraryId);

    Optional<Folder> findByIdAndOwnerIdAndLibraryId(String id, String ownerId, String libraryId);

    long deleteByIdAndOwnerIdAndLibraryId(String id, String ownerId, String libraryId);
}
