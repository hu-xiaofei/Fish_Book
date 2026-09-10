package com.fishbook.administration.application;

import com.fishbook.catalog.domain.PublicationStatus;
import java.time.Instant;

public record AdminFishSummaryView(
        long id,
        String slug,
        String commonNameZh,
        String scientificName,
        PublicationStatus status,
        Instant updatedAt) {}
