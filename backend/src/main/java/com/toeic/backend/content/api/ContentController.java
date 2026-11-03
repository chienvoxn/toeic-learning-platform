package com.toeic.backend.content.api;

import com.toeic.backend.content.application.ContentDtos;
import com.toeic.backend.content.application.ContentQuery;
import com.toeic.backend.content.application.ContentReadService;
import java.util.UUID;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contents")
public class ContentController {
    private final ContentReadService contents;

    public ContentController(ContentReadService contents) { this.contents = contents; }

    @GetMapping
    public ContentDtos.ContentPage list(@RequestParam MultiValueMap<String, String> params) {
        return contents.list(ContentQuery.parse(params));
    }

    @GetMapping("/{id}")
    public ContentDtos.Detail detail(@PathVariable UUID id) { return contents.detail(id); }
}
