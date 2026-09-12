package com.fishbook.administration.photos.application;

import java.time.Instant;

public record AdminPhotoOperation(
        long id, long actorUserId, long ownerUserId, long recordId, String operation,
        String previousRevision, Instant occurredAt) {}
