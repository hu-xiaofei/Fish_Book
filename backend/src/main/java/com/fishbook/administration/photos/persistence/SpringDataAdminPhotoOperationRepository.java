package com.fishbook.administration.photos.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAdminPhotoOperationRepository extends JpaRepository<AdminPhotoOperationJpaEntity, Long> {
    Page<AdminPhotoOperationJpaEntity> findByRecordId(long recordId, Pageable pageable);
}
