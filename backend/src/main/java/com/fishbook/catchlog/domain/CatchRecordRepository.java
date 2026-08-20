package com.fishbook.catchlog.domain;

import java.util.Optional;

public interface CatchRecordRepository {
    CatchRecord save(CatchRecord record);

    Optional<CatchRecord> findByIdAndUserId(long id, long userId);

    CatchRecordPage findByUserId(long userId, int page, int size);

    boolean deleteByIdAndUserId(long id, long userId);
}
