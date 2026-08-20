package com.fishbook.catchlog.persistence;

import com.fishbook.catchlog.domain.CatchRecord;
import com.fishbook.catchlog.domain.CatchRecordDetails;
import com.fishbook.catchlog.domain.CatchRecordPage;
import com.fishbook.catchlog.domain.CatchRecordRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaCatchRecordRepositoryAdapter implements CatchRecordRepository {
    private final SpringDataCatchRecordJpaRepository repository;

    public JpaCatchRecordRepositoryAdapter(SpringDataCatchRecordJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public CatchRecord save(CatchRecord record) {
        return toDomain(repository.save(toEntity(record)));
    }

    @Override
    public Optional<CatchRecord> findByIdAndUserId(long id, long userId) {
        return repository.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    public CatchRecordPage findByUserId(long userId, int page, int size) {
        Page<CatchRecordJpaEntity> result = repository.findByUserId(
                userId,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.desc("caughtOn"),
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id"))));
        List<CatchRecord> items = result.getContent().stream().map(this::toDomain).toList();
        return new CatchRecordPage(
                items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional
    public boolean deleteByIdAndUserId(long id, long userId) {
        return repository.deleteByIdAndUserId(id, userId) > 0;
    }

    private CatchRecord toDomain(CatchRecordJpaEntity entity) {
        return CatchRecord.restore(
                entity.getId(),
                entity.getUserId(),
                new CatchRecordDetails(
                        entity.getFishSpeciesId(),
                        entity.getCaughtOn(),
                        entity.getLocation(),
                        entity.getLengthCm(),
                        entity.getWeightG(),
                        entity.getMethod(),
                        entity.getNotes()),
                entity.getPhotoObjectKey(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private CatchRecordJpaEntity toEntity(CatchRecord record) {
        CatchRecordJpaEntity entity = new CatchRecordJpaEntity();
        entity.setId(record.id());
        entity.setUserId(record.userId());
        entity.setFishSpeciesId(record.details().fishId());
        entity.setCaughtOn(record.details().caughtOn());
        entity.setLocation(record.details().location());
        entity.setLengthCm(record.details().lengthCm());
        entity.setWeightG(record.details().weightG());
        entity.setMethod(record.details().method());
        entity.setNotes(record.details().notes());
        entity.setPhotoObjectKey(record.photoObjectKey());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());
        return entity;
    }
}
