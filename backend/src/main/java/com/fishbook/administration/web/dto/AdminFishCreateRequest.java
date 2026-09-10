package com.fishbook.administration.web.dto;

import com.fishbook.administration.application.CreateFishCommand;
import com.fishbook.administration.application.FishContentCommand;
import com.fishbook.catalog.domain.HabitatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record AdminFishCreateRequest(
        @NotBlank @Size(max = 120) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        @NotBlank @Size(max = 100) String commonNameZh,
        @NotBlank @Size(max = 160) String scientificName,
        @NotBlank @Size(max = 100) String familyNameZh,
        @NotBlank @Size(max = 160) String familyScientificName,
        @NotBlank @Size(max = 100) String genusNameZh,
        @NotBlank @Size(max = 160) String genusScientificName,
        @NotEmpty List<@NotBlank @Size(max = 100) String> aliases,
        @NotEmpty Set<@NotNull HabitatType> habitats,
        @NotBlank @Size(max = 10_000) String appearance,
        @NotBlank @Size(max = 10_000) String sizeDescription,
        @NotBlank @Size(max = 10_000) String habitatDescription,
        @NotBlank @Size(max = 10_000) String distribution,
        @NotBlank @Size(max = 10_000) String description,
        @NotBlank @Size(max = 255) @Pattern(regexp = "/images/fish/[a-z0-9-]+\\.(jpg|jpeg|png|webp)") String imagePath,
        @NotBlank @Size(max = 255) String imageAltText,
        @NotBlank @Size(max = 1000) @Pattern(regexp = "https?://.+") String imageSourceUrl,
        @NotBlank @Size(max = 255) String imageAuthor,
        @NotBlank @Size(max = 100) String imageLicenseName,
        @NotBlank @Size(max = 1000) @Pattern(regexp = "https?://.+") String imageLicenseUrl,
        @Positive int displayOrder) {

    public CreateFishCommand toCommand() {
        return new CreateFishCommand(slug, contentCommand());
    }

    private FishContentCommand contentCommand() {
        return new FishContentCommand(
                commonNameZh, scientificName, familyNameZh, familyScientificName, genusNameZh,
                genusScientificName, aliases, new LinkedHashSet<>(habitats), appearance, sizeDescription,
                habitatDescription, distribution, description, imagePath, imageAltText, imageSourceUrl,
                imageAuthor, imageLicenseName, imageLicenseUrl, displayOrder);
    }
}
