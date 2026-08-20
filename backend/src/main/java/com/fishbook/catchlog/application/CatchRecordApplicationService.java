package com.fishbook.catchlog.application;

public interface CatchRecordApplicationService {
    CatchRecordDetailView create(String authenticatedEmail, CatchRecordCommand command);

    CatchRecordPageView list(String authenticatedEmail, int page);

    CatchRecordDetailView get(String authenticatedEmail, long id);

    CatchRecordDetailView update(String authenticatedEmail, long id, CatchRecordCommand command);

    void delete(String authenticatedEmail, long id);
}
