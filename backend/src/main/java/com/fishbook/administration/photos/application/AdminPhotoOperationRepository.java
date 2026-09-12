package com.fishbook.administration.photos.application;

import java.time.Instant;
import org.springframework.data.domain.Page;

public interface AdminPhotoOperationRepository {
    void append(long actorId, long ownerId, long recordId, String operation, long previousVersion, Instant at);

    Page<AdminPhotoOperation> findByRecordId(long recordId, int page, int size);
}
