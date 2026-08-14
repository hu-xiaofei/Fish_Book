package com.fishbook.favorites.web;

import com.fishbook.favorites.application.FavoriteApplicationService;
import com.fishbook.favorites.application.InvalidFavoriteQueryException;
import com.fishbook.favorites.web.dto.FavoritePageResponse;
import com.fishbook.favorites.web.dto.FavoriteStatusResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private final FavoriteApplicationService service;

    public FavoriteController(FavoriteApplicationService service) {
        this.service = service;
    }

    @PutMapping("/{fishSlug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void add(Authentication authentication, @PathVariable String fishSlug) {
        service.add(authentication.getName(), fishSlug);
    }

    @DeleteMapping("/{fishSlug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(Authentication authentication, @PathVariable String fishSlug) {
        service.remove(authentication.getName(), fishSlug);
    }

    @GetMapping
    FavoritePageResponse list(
            Authentication authentication,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        if (size != null) {
            throw new InvalidFavoriteQueryException("size is fixed and must not be provided");
        }
        return FavoritePageResponse.from(service.list(authentication.getName(), parsePage(page)));
    }

    @GetMapping("/status")
    FavoriteStatusResponse statuses(
            Authentication authentication,
            @RequestParam("fishSlug") List<String> fishSlugs) {
        return FavoriteStatusResponse.from(service.statuses(authentication.getName(), fishSlugs));
    }

    private static int parsePage(String page) {
        if (page == null) {
            return 0;
        }
        try {
            return Integer.parseInt(page);
        } catch (NumberFormatException exception) {
            throw new InvalidFavoriteQueryException("page must be a non-negative integer");
        }
    }
}
