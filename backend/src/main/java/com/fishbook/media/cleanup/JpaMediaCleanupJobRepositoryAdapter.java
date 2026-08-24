package com.fishbook.media.cleanup;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaMediaCleanupJobRepositoryAdapter implements MediaCleanupJobRepository {
    private final SpringDataMediaCleanupJobJpaRepository repository;

    public JpaMediaCleanupJobRepositoryAdapter(
            SpringDataMediaCleanupJobJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public MediaCleanupJob enqueue(
            String objectKey, MediaCleanupReason reason, Instant now) {
        return toDomain(repository.save(toEntity(MediaCleanupJob.pending(objectKey, reason, now))));
    }

    @Override
    public List<MediaCleanupJob> findDue(Instant now, int limit) {
        return repository
                .findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
                        MediaCleanupStatus.PENDING, now, PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void save(MediaCleanupJob job) {
        repository.save(toEntity(job));
    }

    @Override
    @Transactional
    public void delete(long jobId) {
        repository.deleteById(jobId);
    }

    private MediaCleanupJob toDomain(MediaCleanupJobJpaEntity entity) {
        return new MediaCleanupJob(
                entity.getId(),
                entity.getObjectKey(),
                entity.getReason(),
                entity.getStatus(),
                entity.getAttemptCount(),
                entity.getNextAttemptAt(),
                entity.getLastAttemptAt(),
                entity.getCreatedAt());
    }

    private MediaCleanupJobJpaEntity toEntity(MediaCleanupJob job) {
        var entity = new MediaCleanupJobJpaEntity();
        entity.setId(job.id());
        entity.setObjectKey(job.objectKey());
        entity.setReason(job.reason());
        entity.setStatus(job.status());
        entity.setAttemptCount(job.attemptCount());
        entity.setNextAttemptAt(job.nextAttemptAt());
        entity.setLastAttemptAt(job.lastAttemptAt());
        entity.setCreatedAt(job.createdAt());
        return entity;
    }
}
