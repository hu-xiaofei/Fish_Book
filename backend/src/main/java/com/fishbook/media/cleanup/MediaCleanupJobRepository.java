package com.fishbook.media.cleanup;

import java.time.Instant;
import java.util.List;

public interface MediaCleanupJobRepository {
    MediaCleanupJob enqueue(String objectKey, MediaCleanupReason reason, Instant now);

    List<MediaCleanupJob> findDue(Instant now, int limit);

    void save(MediaCleanupJob job);

    void delete(long jobId);
}
