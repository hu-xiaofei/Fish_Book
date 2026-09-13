package com.fishbook.administration.photos.application;

import java.util.List;

public record AdminPhotoOperationPageView(List<AdminPhotoOperation> items, int page, int size, long totalItems, int totalPages) {}
