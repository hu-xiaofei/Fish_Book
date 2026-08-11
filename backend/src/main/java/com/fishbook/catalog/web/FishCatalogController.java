package com.fishbook.catalog.web;

import com.fishbook.catalog.application.FishCatalogQuery;
import com.fishbook.catalog.application.FishCatalogQueryService;
import com.fishbook.catalog.web.dto.FishDetailResponse;
import com.fishbook.catalog.web.dto.FishFilterOptionsResponse;
import com.fishbook.catalog.web.dto.FishPageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fish")
public class FishCatalogController {

    private final FishCatalogQueryService service;

    public FishCatalogController(FishCatalogQueryService service) {
        this.service = service;
    }

    @GetMapping
    FishPageResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String family,
            @RequestParam(required = false) String habitat,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return FishPageResponse.from(service.search(
                FishCatalogQuery.from(q, family, habitat, page, size)));
    }

    @GetMapping("/filters")
    FishFilterOptionsResponse filters() {
        return FishFilterOptionsResponse.from(service.getFilterOptions());
    }

    @GetMapping("/{slug}")
    FishDetailResponse detail(@PathVariable String slug) {
        return FishDetailResponse.from(service.getBySlug(slug));
    }
}
