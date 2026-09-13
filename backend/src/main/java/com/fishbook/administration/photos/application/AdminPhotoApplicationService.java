package com.fishbook.administration.photos.application;

public interface AdminPhotoApplicationService {
    AdminPhotoPageView search(String email, AdminPhotoQuery query);
    AdminPhotoSummaryView get(String email, long recordId);
    AdminPhotoOperationPageView operations(String email, long recordId, int page, int size);
}
