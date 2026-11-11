package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ExamMediaController {
    private final ExamMediaService media;
    public ExamMediaController(ExamMediaService media){this.media=media;}
    @PostMapping(value="/teacher/media",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Object upload(@AuthenticationPrincipal SessionPrincipal p,@RequestPart("file") MultipartFile file)throws IOException{return media.upload(p.userId(),file.getBytes());}
    @GetMapping("/media/{id}")
    public ResponseEntity<ByteArrayResource> download(@AuthenticationPrincipal SessionPrincipal p,@PathVariable UUID id){var content=media.download(p.userId(),id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.mimeType())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(new ByteArrayResource(content.data()));}
}
