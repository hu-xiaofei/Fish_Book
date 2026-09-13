package com.fishbook.administration.photos.application;

import java.util.Optional;

public interface AdminPhotoQueryRepository {
    AdminPhotoPageView search(AdminPhotoQuery query);
    Optional<AdminPhotoSummaryView> findByRecordId(long recordId);
}
