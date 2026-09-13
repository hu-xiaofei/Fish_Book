package com.fishbook.administration.photos.application;

import java.util.List;

public record AdminPhotoPageView(List<AdminPhotoSummaryView> items, int page, int size, long totalItems, int totalPages) {}
