package com.fishbook.administration.application;

import com.fishbook.catalog.domain.FishSpeciesContent;
import com.fishbook.catalog.domain.HabitatType;
import com.fishbook.catalog.domain.ImageAttribution;
import java.util.List;
import java.util.Set;

public record FishContentCommand(
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
        int displayOrder) {

    FishSpeciesContent toDomain() {
        return new FishSpeciesContent(
                commonNameZh, scientificName, familyNameZh, familyScientificName,
                genusNameZh, genusScientificName, aliases, habitats, appearance,
                sizeDescription, habitatDescription, distribution, description,
                new ImageAttribution(
                        imagePath, imageAltText, imageSourceUrl, imageAuthor,
                        imageLicenseName, imageLicenseUrl),
                displayOrder);
    }
}
