package com.fishbook.media.cleanup;

import com.fishbook.media.domain.MediaStore;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaCleanupService.class);
    private static final int BATCH_SIZE = 20;

    private final MediaCleanupJobRepository repository;
    private final MediaStore mediaStore;
    private final Clock clock;

    public MediaCleanupService(
            MediaCleanupJobRepository repository, MediaStore mediaStore, Clock clock) {
        this.repository = repository;
        this.mediaStore = mediaStore;
        this.clock = clock;
    }

    @Transactional
    public MediaCleanupJob enqueue(
            String objectKey, MediaCleanupReason reason, Instant now) {
        return repository.enqueue(objectKey, reason, now);
    }

    @Scheduled(fixedDelayString = "${fishbook.media.cleanup-delay:PT1M}")
    public void processDueJobs() {
        var attemptedAt = clock.instant();
        for (var job : repository.findDue(attemptedAt, BATCH_SIZE)) {
            process(job, attemptedAt);
        }
    }

    private void process(MediaCleanupJob job, Instant attemptedAt) {
        try {
            mediaStore.delete(job.objectKey());
            repository.delete(job.id());
        } catch (RuntimeException exception) {
            var updated = job.recordFailure(attemptedAt);
            repository.save(updated);
            if (updated.status() == MediaCleanupStatus.FAILED) {
                LOGGER.error(
                        "Media cleanup exhausted retries: jobId={}, reason={}, attemptCount={}, errorClass={}",
                        updated.id(), updated.reason(), updated.attemptCount(),
                        exception.getClass().getSimpleName());
            } else {
                LOGGER.warn(
                        "Media cleanup will retry: jobId={}, reason={}, attemptCount={}, errorClass={}",
                        updated.id(), updated.reason(), updated.attemptCount(),
                        exception.getClass().getSimpleName());
            }
        }
    }
}
