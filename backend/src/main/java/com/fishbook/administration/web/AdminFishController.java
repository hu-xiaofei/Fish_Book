package com.fishbook.administration.web;

import com.fishbook.administration.application.AdminFishQuery;
import com.fishbook.administration.application.FishAdministrationService;
import com.fishbook.administration.application.InvalidAdminFishQueryException;
import com.fishbook.administration.web.dto.AdminFishCreateRequest;
import com.fishbook.administration.web.dto.AdminFishDetailResponse;
import com.fishbook.administration.web.dto.AdminFishPageResponse;
import com.fishbook.administration.web.dto.AdminFishUpdateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/fishes")
public final class AdminFishController {

    private final FishAdministrationService service;

    public AdminFishController(FishAdministrationService service) {
        this.service = service;
    }

    @GetMapping
    AdminFishPageResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return AdminFishPageResponse.from(service.search(AdminFishQuery.from(q, status, page, size)));
    }

    @GetMapping("/{id}")
    AdminFishDetailResponse get(@PathVariable String id) {
        return AdminFishDetailResponse.from(service.get(parseId(id)));
    }

    @PostMapping
    ResponseEntity<AdminFishDetailResponse> create(@Valid @RequestBody AdminFishCreateRequest request) {
        AdminFishDetailResponse response = AdminFishDetailResponse.from(service.create(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/admin/fishes/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    AdminFishDetailResponse update(
            @PathVariable String id,
            @Valid @RequestBody AdminFishUpdateRequest request) {
        return AdminFishDetailResponse.from(service.update(parseId(id), request.toCommand()));
    }

    @PostMapping("/{id}/publish")
    AdminFishDetailResponse publish(@PathVariable String id) {
        return AdminFishDetailResponse.from(service.publish(parseId(id)));
    }

    @PostMapping("/{id}/unpublish")
    AdminFishDetailResponse unpublish(@PathVariable String id) {
        return AdminFishDetailResponse.from(service.unpublish(parseId(id)));
    }

    private long parseId(String rawId) {
        try {
            long id = Long.parseLong(rawId);
            if (id <= 0) {
                throw new NumberFormatException("non-positive id");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new InvalidAdminFishQueryException("id must be a positive integer");
        }
    }
}
