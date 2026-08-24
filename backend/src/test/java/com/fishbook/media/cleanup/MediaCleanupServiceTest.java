package com.fishbook.media.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishbook.media.domain.MediaStorageUnavailableException;
import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.domain.StoredMedia;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class MediaCleanupServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T01:00:00Z");

    @Test
    void enqueueCreatesPendingJobDueAtRequestedTime() {
        var repository = new InMemoryCleanupRepository();
        var service = new MediaCleanupService(repository, new FakeMediaStore(), Clock.fixed(NOW, ZoneOffset.UTC));

        var job = service.enqueue("catches/1/2/old", MediaCleanupReason.REPLACED, NOW);

        assertThat(job.id()).isPositive();
        assertThat(job.objectKey()).isEqualTo("catches/1/2/old");
        assertThat(job.reason()).isEqualTo(MediaCleanupReason.REPLACED);
        assertThat(job.status()).isEqualTo(MediaCleanupStatus.PENDING);
        assertThat(job.attemptCount()).isZero();
        assertThat(job.nextAttemptAt()).isEqualTo(NOW);
        assertThat(job.lastAttemptAt()).isNull();
        assertThat(job.createdAt()).isEqualTo(NOW);
    }

    @Test
    void successfulDeleteRemovesCleanupJob() {
        var repository = repositoryWithPendingJobs(1, NOW);
        var mediaStore = new FakeMediaStore();
        var service = new MediaCleanupService(repository, mediaStore, Clock.fixed(NOW, ZoneOffset.UTC));

        service.processDueJobs();

        assertThat(repository.jobs).isEmpty();
        assertThat(mediaStore.deletedKeys).containsExactly("catches/1/1/old");
    }

    @Test
    void failuresBackOffExponentiallyThenRemainFailedAfterEighthAttempt() {
        var repository = repositoryWithPendingJobs(1, NOW);
        var mediaStore = new FakeMediaStore();
        mediaStore.failDeletes = true;
        var clock = new MutableClock(NOW);
        var service = new MediaCleanupService(repository, mediaStore, clock);
        var expectedDelayMinutes = List.of(1L, 2L, 4L, 8L, 16L, 32L, 64L);

        for (var attempt = 1; attempt <= expectedDelayMinutes.size(); attempt++) {
            var attemptedAt = clock.instant();
            service.processDueJobs();

            var job = repository.jobs.getFirst();
            assertThat(job.status()).isEqualTo(MediaCleanupStatus.PENDING);
            assertThat(job.attemptCount()).isEqualTo(attempt);
            assertThat(job.lastAttemptAt()).isEqualTo(attemptedAt);
            assertThat(job.nextAttemptAt())
                    .isEqualTo(attemptedAt.plus(Duration.ofMinutes(expectedDelayMinutes.get(attempt - 1))));
            clock.set(job.nextAttemptAt());
        }

        var eighthAttemptAt = clock.instant();
        service.processDueJobs();

        var failed = repository.jobs.getFirst();
        assertThat(failed.status()).isEqualTo(MediaCleanupStatus.FAILED);
        assertThat(failed.attemptCount()).isEqualTo(8);
        assertThat(failed.lastAttemptAt()).isEqualTo(eighthAttemptAt);
        assertThat(failed.nextAttemptAt()).isNull();
    }

    @Test
    void failedJobsAreNotSelectedAgain() {
        var failed = new MediaCleanupJob(
                1L,
                "catches/1/2/failed",
                MediaCleanupReason.REMOVED,
                MediaCleanupStatus.FAILED,
                8,
                null,
                NOW,
                NOW.minusSeconds(60));
        var repository = new InMemoryCleanupRepository(List.of(failed));
        var mediaStore = new FakeMediaStore();
        var service = new MediaCleanupService(repository, mediaStore, Clock.fixed(NOW, ZoneOffset.UTC));

        service.processDueJobs();

        assertThat(mediaStore.deletedKeys).isEmpty();
        assertThat(repository.jobs).containsExactly(failed);
    }

    @Test
    void processesAtMostTwentyDueJobsPerTick() {
        var repository = repositoryWithPendingJobs(25, NOW);
        var mediaStore = new FakeMediaStore();
        var service = new MediaCleanupService(repository, mediaStore, Clock.fixed(NOW, ZoneOffset.UTC));

        service.processDueJobs();

        assertThat(mediaStore.deletedKeys).hasSize(20);
        assertThat(repository.jobs).hasSize(5);
        assertThat(repository.lastRequestedLimit).isEqualTo(20);
    }

    private static InMemoryCleanupRepository repositoryWithPendingJobs(int count, Instant dueAt) {
        var jobs = new ArrayList<MediaCleanupJob>();
        for (long id = 1; id <= count; id++) {
            jobs.add(new MediaCleanupJob(
                    id,
                    "catches/1/" + id + "/old",
                    MediaCleanupReason.RECORD_DELETED,
                    MediaCleanupStatus.PENDING,
                    0,
                    dueAt,
                    null,
                    dueAt));
        }
        return new InMemoryCleanupRepository(jobs);
    }

    private static final class InMemoryCleanupRepository implements MediaCleanupJobRepository {
        private final List<MediaCleanupJob> jobs = new ArrayList<>();
        private long nextId = 1L;
        private int lastRequestedLimit;

        private InMemoryCleanupRepository() {}

        private InMemoryCleanupRepository(List<MediaCleanupJob> jobs) {
            this.jobs.addAll(jobs);
            this.nextId = jobs.stream()
                    .map(MediaCleanupJob::id)
                    .max(Long::compareTo)
                    .orElse(0L) + 1L;
        }

        @Override
        public MediaCleanupJob enqueue(String objectKey, MediaCleanupReason reason, Instant now) {
            var job = new MediaCleanupJob(
                    nextId++, objectKey, reason, MediaCleanupStatus.PENDING,
                    0, now, null, now);
            jobs.add(job);
            return job;
        }

        @Override
        public List<MediaCleanupJob> findDue(Instant now, int limit) {
            lastRequestedLimit = limit;
            return jobs.stream()
                    .filter(job -> job.status() == MediaCleanupStatus.PENDING)
                    .filter(job -> !job.nextAttemptAt().isAfter(now))
                    .sorted(Comparator.comparing(MediaCleanupJob::nextAttemptAt)
                            .thenComparing(MediaCleanupJob::id))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void save(MediaCleanupJob job) {
            jobs.replaceAll(existing -> existing.id().equals(job.id()) ? job : existing);
        }

        @Override
        public void delete(long jobId) {
            jobs.removeIf(job -> job.id() == jobId);
        }
    }

    private static final class FakeMediaStore implements MediaStore {
        private final List<String> deletedKeys = new ArrayList<>();
        private boolean failDeletes;

        @Override
        public void put(String objectKey, byte[] content, String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredMedia get(String objectKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String objectKey) {
            deletedKeys.add(objectKey);
            if (failDeletes) {
                throw new MediaStorageUnavailableException();
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void set(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
