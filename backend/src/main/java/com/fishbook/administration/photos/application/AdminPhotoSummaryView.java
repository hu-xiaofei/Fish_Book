package com.fishbook.administration.photos.application;

import java.time.Instant;
import java.time.LocalDate;

public record AdminPhotoSummaryView(long recordId, long ownerUserId, String ownerNickname,
        String commonNameZh, LocalDate caughtOn, boolean hasPhoto, String revision, Instant updatedAt) {}
