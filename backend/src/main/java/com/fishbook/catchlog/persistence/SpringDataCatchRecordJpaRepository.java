package com.fishbook.catchlog.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataCatchRecordJpaRepository
        extends JpaRepository<CatchRecordJpaEntity, Long> {
    Optional<CatchRecordJpaEntity> findByIdAndUserId(long id, long userId);

    Page<CatchRecordJpaEntity> findByUserId(long userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM CatchRecordJpaEntity record "
            + "WHERE record.id = :id AND record.userId = :userId")
    long deleteByIdAndUserId(@Param("id") long id, @Param("userId") long userId);
}
