package com.fishbook.catchlog.web;

import com.fishbook.catchlog.application.CatchPhotoApplicationService;
import com.fishbook.catchlog.application.InvalidCatchRecordQueryException;
import java.io.IOException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/catches/{id}/photo")
public class CatchPhotoController {
    private final CatchPhotoApplicationService service;

    public CatchPhotoController(CatchPhotoApplicationService service) {
        this.service = service;
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void put(
            Authentication authentication,
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestPart("photo") MultipartFile photo) throws IOException {
        long version = PhotoRevision.parse(ifMatch);
        service.put(
                authentication.getName(),
                parseId(id),
                photo.getBytes(),
                photo.getContentType(), version);
    }

    @GetMapping
    ResponseEntity<byte[]> get(Authentication authentication, @PathVariable String id) {
        var photo = service.get(authentication.getName(), parseId(id));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .eTag(photo.revision())
                .body(photo.content());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(Authentication authentication, @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        service.remove(authentication.getName(), parseId(id), PhotoRevision.parse(ifMatch));
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new InvalidCatchRecordQueryException("catch record ID must be an integer");
        }
    }
}
