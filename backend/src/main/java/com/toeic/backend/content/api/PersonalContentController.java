package com.toeic.backend.content.api;

import com.toeic.backend.content.application.*;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PersonalContentController {
    private final ContentPersonalService contents;
    public PersonalContentController(ContentPersonalService contents) { this.contents = contents; }

    private static void noPayload(MultiValueMap<String, String> params, byte[] body) {
        if (!params.isEmpty() || body != null && body.length > 0) { throw ContentException.invalidQuery(); }
    }

    @PostMapping("/contents/{id}/bookmarks")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalContentDtos.BookmarkReceipt bookmark(@AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID id, @RequestParam MultiValueMap<String, String> params,
            @RequestBody(required = false) byte[] body) {
        noPayload(params, body);
        return contents.bookmark(principal.userId(), id);
    }

    @DeleteMapping("/contents/{id}/bookmarks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeBookmark(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID id,
            @RequestParam MultiValueMap<String, String> params, @RequestBody(required = false) byte[] body) {
        noPayload(params, body);
        contents.removeBookmark(principal.userId(), id);
    }

    @GetMapping("/users/me/bookmarks")
    public PersonalContentDtos.Page<PersonalContentDtos.Bookmark> bookmarks(@AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam MultiValueMap<String, String> params) {
        return contents.bookmarks(principal.userId(), PersonalContentQuery.parse(params, PersonalContentQuery.Kind.BOOKMARKS));
    }

    @PostMapping("/contents/{id}/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordHistory(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID id,
            @RequestParam MultiValueMap<String, String> params, @RequestBody(required = false) byte[] body) {
        noPayload(params, body);
        contents.recordHistory(principal.userId(), id);
    }

    @GetMapping("/users/me/content-history")
    public PersonalContentDtos.Page<PersonalContentDtos.History> history(@AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam MultiValueMap<String, String> params) {
        return contents.history(principal.userId(), PersonalContentQuery.parse(params, PersonalContentQuery.Kind.HISTORY));
    }
}
