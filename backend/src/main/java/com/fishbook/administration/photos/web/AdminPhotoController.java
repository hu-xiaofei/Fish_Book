package com.fishbook.administration.photos.web;

import com.fishbook.administration.photos.application.AdminPhotoApplicationService;
import com.fishbook.administration.photos.application.AdminPhotoOperationPageView;
import com.fishbook.administration.photos.application.AdminPhotoPageView;
import com.fishbook.administration.photos.application.AdminPhotoQuery;
import com.fishbook.administration.photos.application.AdminPhotoSummaryView;
import com.fishbook.catchlog.application.CatchPhotoApplicationService;
import com.fishbook.catchlog.web.PhotoRevision;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/photos")
public class AdminPhotoController {
    private final AdminPhotoApplicationService administration;
    private final CatchPhotoApplicationService photos;
    public AdminPhotoController(AdminPhotoApplicationService administration, CatchPhotoApplicationService photos) {
        this.administration = administration;
        this.photos = photos;
    }
    @GetMapping
    ResponseEntity<AdminPhotoPageView> search(Authentication auth,
            @RequestParam(required=false) String userId, @RequestParam(required=false) String page,
            @RequestParam(required=false) String size) {
        return privateOk(administration.search(auth.getName(), AdminPhotoQuery.from(userId, page, size)));
    }
    @GetMapping("/{recordId}")
    ResponseEntity<AdminPhotoSummaryView> get(Authentication auth, @PathVariable String recordId) {
        return privateOk(administration.get(auth.getName(), AdminPhotoQuery.positiveId(recordId)));
    }
    @GetMapping("/{recordId}/operations")
    ResponseEntity<AdminPhotoOperationPageView> operations(Authentication auth, @PathVariable String recordId,
            @RequestParam(required=false) String page, @RequestParam(required=false) String size) {
        var query = AdminPhotoQuery.from(null, page, size);
        return privateOk(administration.operations(auth.getName(), AdminPhotoQuery.positiveId(recordId), query.page(), query.size()));
    }
    @GetMapping("/{recordId}/content")
    ResponseEntity<byte[]> content(Authentication auth, @PathVariable String recordId) {
        var photo = photos.getForAdmin(auth.getName(), AdminPhotoQuery.positiveId(recordId));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline").header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").eTag(photo.revision()).body(photo.content());
    }
    @PutMapping(value="/{recordId}/content", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Void> put(Authentication auth, @PathVariable String recordId,
            @RequestHeader(value="If-Match", required=false) String ifMatch, @RequestPart("photo") MultipartFile photo) throws IOException {
        long version = PhotoRevision.parse(ifMatch);
        photos.putForAdmin(auth.getName(), AdminPhotoQuery.positiveId(recordId), photo.getBytes(), photo.getContentType(), version);
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "private, no-store").build();
    }
    @DeleteMapping("/{recordId}/content")
    ResponseEntity<Void> remove(Authentication auth, @PathVariable String recordId,
            @RequestHeader(value="If-Match", required=false) String ifMatch) {
        photos.removeForAdmin(auth.getName(), AdminPhotoQuery.positiveId(recordId), PhotoRevision.parse(ifMatch));
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "private, no-store").build();
    }
    private static <T> ResponseEntity<T> privateOk(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(body);
    }
}
