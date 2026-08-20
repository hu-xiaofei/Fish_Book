package com.fishbook.catchlog.web;

import com.fishbook.catchlog.application.CatchRecordApplicationService;
import com.fishbook.catchlog.application.InvalidCatchRecordQueryException;
import com.fishbook.catchlog.web.dto.CatchRecordDetailResponse;
import com.fishbook.catchlog.web.dto.CatchRecordPageResponse;
import com.fishbook.catchlog.web.dto.CatchRecordRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catches")
public class CatchRecordController {

    private final CatchRecordApplicationService service;

    public CatchRecordController(CatchRecordApplicationService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<CatchRecordDetailResponse> create(
            Authentication authentication, @RequestBody CatchRecordRequest request) {
        CatchRecordDetailResponse response = CatchRecordDetailResponse.from(
                service.create(authentication.getName(), request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/catches/" + response.id())).body(response);
    }

    @GetMapping
    CatchRecordPageResponse list(
            Authentication authentication,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        if (size != null) {
            throw new InvalidCatchRecordQueryException("size is fixed and must not be provided");
        }
        return CatchRecordPageResponse.from(service.list(authentication.getName(), parsePage(page)));
    }

    @GetMapping("/{id}")
    CatchRecordDetailResponse get(Authentication authentication, @PathVariable String id) {
        return CatchRecordDetailResponse.from(service.get(authentication.getName(), parseId(id)));
    }

    @PutMapping("/{id}")
    CatchRecordDetailResponse update(
            Authentication authentication, @PathVariable String id, @RequestBody CatchRecordRequest request) {
        return CatchRecordDetailResponse.from(
                service.update(authentication.getName(), parseId(id), request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication authentication, @PathVariable String id) {
        service.delete(authentication.getName(), parseId(id));
    }

    private static int parsePage(String page) {
        if (page == null) {
            return 0;
        }
        try {
            return Integer.parseInt(page);
        } catch (NumberFormatException exception) {
            throw new InvalidCatchRecordQueryException("page must be a non-negative integer");
        }
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new InvalidCatchRecordQueryException("catch record ID must be an integer");
        }
    }
}
