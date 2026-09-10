package com.fishbook.administration.web.dto;

import com.fishbook.administration.application.AdminFishDetailView;
import com.fishbook.catalog.domain.HabitatType;
import com.fishbook.catalog.domain.PublicationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record AdminFishDetailResponse(
        long id,
        String slug,
        String commonNameZh,
        String scientificName,
        String familyNameZh,
        String familyScientificName,
        String genusNameZh,
        String genusScientificName,
        List<String> aliases,
        Set<HabitatType> habitats,
        String appearance,
        String sizeDescription,
        String habitatDescription,
        String distribution,
        String description,
        String imagePath,
        String imageAltText,
        String imageSourceUrl,
        String imageAuthor,
        String imageLicenseName,
        String imageLicenseUrl,
        int displayOrder,
        PublicationStatus status,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {

    public AdminFishDetailResponse {
        aliases = List.copyOf(aliases);
        habitats = Set.copyOf(habitats);
    }

    public static AdminFishDetailResponse from(AdminFishDetailView view) {
        return new AdminFishDetailResponse(
                view.id(), view.slug(), view.commonNameZh(), view.scientificName(),
                view.familyNameZh(), view.familyScientificName(), view.genusNameZh(),
                view.genusScientificName(), view.aliases(), view.habitats(), view.appearance(),
                view.sizeDescription(), view.habitatDescription(), view.distribution(), view.description(),
                view.imagePath(), view.imageAltText(), view.imageSourceUrl(), view.imageAuthor(),
                view.imageLicenseName(), view.imageLicenseUrl(), view.displayOrder(), view.status(),
                view.publishedAt(), view.createdAt(), view.updatedAt());
    }
}
