package com.decksuggester.decks;

import com.decksuggester.InvalidRequestException;
import com.decksuggester.auth.CurrentUserService;
import com.decksuggester.auth.UserIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/folders")
public class FolderController {

    private final FolderService folderService;
    private final CurrentUserService currentUser;

    public FolderController(FolderService folderService, CurrentUserService currentUser) {
        this.folderService = folderService;
        this.currentUser = currentUser;
    }

    @GetMapping({ "", "/" })
    public List<Folder> list() {
        return folderService.list(currentUser.require());
    }

    @PostMapping({ "", "/" })
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public Folder create(@RequestBody FolderRequest request) {
        if (request == null) {
            throw new InvalidRequestException("A JSON body containing a name is required");
        }
        return folderService.create(currentUser.require(), request.name(), request.parentId());
    }

    @PutMapping("/{id}")
    public Folder update(@PathVariable String id, @RequestBody FolderRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new InvalidRequestException("Folder name cannot be blank");
        }
        return folderService.rename(currentUser.require(), id, request.name());
    }

    /** Moves a folder to a new parent and/or position among its siblings. */
    @PutMapping("/{id}/move")
    public Folder move(@PathVariable String id, @RequestBody MoveFolderRequest request) {
        if (request == null) {
            throw new InvalidRequestException("A JSON body describing the move is required");
        }
        return folderService.move(currentUser.require(), id, request.parentId(), request.order());
    }

    /** Recursively deletes a folder and every folder/deck nested inside it. */
    @DeleteMapping("/{id}")
    public ResponseEntity<DeleteFolderResult> delete(@PathVariable String id) {
        UserIdentity owner = currentUser.require();
        int foldersDeleted = folderService.delete(owner, id);
        return ResponseEntity.ok(new DeleteFolderResult(foldersDeleted));
    }

    /** Number of decks contained (recursively) inside a folder. */
    @GetMapping("/{id}/decks/count")
    public long deckCount(@PathVariable String id) {
        return folderService.decksIn(currentUser.require(), id);
    }

    public record FolderRequest(String name, String parentId) {
    }

    public record MoveFolderRequest(String parentId, int order) {
    }

    public record DeleteFolderResult(int foldersDeleted) {
    }
}
