package com.fishbook.media.cleanup;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMediaCleanupJobJpaRepository
        extends JpaRepository<MediaCleanupJobJpaEntity, Long> {
    List<MediaCleanupJobJpaEntity>
            findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
                    MediaCleanupStatus status, Instant nextAttemptAt, Pageable pageable);
}
