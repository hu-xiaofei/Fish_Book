package com.fishbook.administration.photos.application;

import com.fishbook.catchlog.application.CatchPhotoNotFoundException;
import com.fishbook.identity.domain.UserRepository;
import com.fishbook.identity.domain.UserRole;
import com.fishbook.identity.domain.UserStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class DefaultAdminPhotoApplicationService implements AdminPhotoApplicationService {
    private final UserRepository users;
    private final AdminPhotoQueryRepository queries;
    private final AdminPhotoOperationRepository operations;

    public DefaultAdminPhotoApplicationService(UserRepository users, AdminPhotoQueryRepository queries,
            AdminPhotoOperationRepository operations) {
        this.users = users;
        this.queries = queries;
        this.operations = operations;
    }
    @Override public AdminPhotoPageView search(String email, AdminPhotoQuery query) {
        requireAdmin(email);
        if (query == null) throw new InvalidAdminPhotoQueryException();
        return queries.search(query);
    }
    @Override public AdminPhotoSummaryView get(String email, long recordId) {
        requireAdmin(email);
        return detail(recordId);
    }
    @Override public AdminPhotoOperationPageView operations(String email, long recordId, int page, int size) {
        requireAdmin(email);
        new AdminPhotoQuery(null, page, size);
        detail(recordId);
        var result = operations.findByRecordId(recordId, page, size);
        return new AdminPhotoOperationPageView(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }
    private AdminPhotoSummaryView detail(long recordId) {
        if (recordId <= 0) throw new InvalidAdminPhotoQueryException();
        return queries.findByRecordId(recordId).orElseThrow(CatchPhotoNotFoundException::new);
    }
    private void requireAdmin(String email) {
        var user = users.findByEmail(email).orElseThrow(() -> new AccessDeniedException("Access is denied"));
        if (user.role() != UserRole.ADMIN || user.status() != UserStatus.ACTIVE)
            throw new AccessDeniedException("Access is denied");
    }
}
