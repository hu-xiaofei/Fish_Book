package com.fishbook.administration.photos.persistence;

import com.fishbook.administration.photos.application.AdminPhotoOperation;
import com.fishbook.administration.photos.application.AdminPhotoOperationRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaAdminPhotoOperationRepository implements AdminPhotoOperationRepository {
    private final SpringDataAdminPhotoOperationRepository repository;

    public JpaAdminPhotoOperationRepository(SpringDataAdminPhotoOperationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void append(long actorId, long ownerId, long recordId, String operation, long previousVersion, Instant at) {
        repository.saveAndFlush(new AdminPhotoOperationJpaEntity(
                actorId, ownerId, recordId, operation, previousVersion, at));
    }

    @Override
    public Page<AdminPhotoOperation> findByRecordId(long recordId, int page, int size) {
        return repository.findByRecordId(recordId, PageRequest.of(page, size,
                        Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))))
                .map(entity -> new AdminPhotoOperation(entity.getId(), entity.getActorUserId(),
                        entity.getOwnerUserId(), entity.getRecordId(), entity.getOperation(),
                        Long.toString(entity.getPreviousVersion()), entity.getOccurredAt()));
    }
}
