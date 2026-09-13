package com.fishbook.catchlog.application;

import com.fishbook.administration.photos.application.AdminPhotoOperationRepository;
import com.fishbook.catchlog.domain.CatchRecord;
import com.fishbook.catchlog.domain.CatchRecordRepository;
import com.fishbook.identity.application.ProfileApplicationService;
import com.fishbook.identity.domain.UserRepository;
import com.fishbook.identity.domain.UserRole;
import com.fishbook.identity.domain.UserStatus;
import com.fishbook.media.application.CatchPhotoValidator;
import com.fishbook.media.cleanup.MediaCleanupReason;
import com.fishbook.media.cleanup.MediaCleanupService;
import com.fishbook.media.domain.MediaStore;
import jakarta.persistence.OptimisticLockException;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DefaultCatchPhotoApplicationService implements CatchPhotoApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCatchPhotoApplicationService.class);
    private final ProfileApplicationService profiles;
    private final UserRepository users;
    private final CatchRecordRepository records;
    private final CatchPhotoValidator validator;
    private final MediaStore store;
    private final MediaCleanupService cleanup;
    private final AdminPhotoOperationRepository operations;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DefaultCatchPhotoApplicationService(ProfileApplicationService profiles,
            CatchRecordRepository records, CatchPhotoValidator validator, MediaStore store,
            MediaCleanupService cleanup, Clock clock, TransactionTemplate transaction,
            UserRepository users, AdminPhotoOperationRepository operations) {
        this.profiles = profiles;
        this.records = records;
        this.validator = validator;
        this.store = store;
        this.cleanup = cleanup;
        this.clock = clock;
        this.users = users;
        this.operations = operations;
        // Each orchestration owns its commit boundary; compensation never joins a failed caller.
        this.transaction = new TransactionTemplate(transaction.getTransactionManager());
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override public void put(String email, long id, byte[] content, String contentType, long version) {
        put(email, id, content, contentType, version, false);
    }
    @Override public void putForAdmin(String email, long id, byte[] content, String contentType, long version) {
        put(email, id, content, contentType, version, true);
    }
    @Override public CatchPhotoView get(String email, long id) { return get(email, id, false); }
    @Override public CatchPhotoView getForAdmin(String email, long id) { return get(email, id, true); }
    @Override public void remove(String email, long id, long version) { remove(email, id, version, false); }
    @Override public void removeForAdmin(String email, long id, long version) { remove(email, id, version, true); }

    private void put(String email, long id, byte[] content, String contentType, long version, boolean admin) {
        var original = transaction.execute(status -> {
            long actorId = actor(email, admin);
            var record = record(id, actorId, admin, false);
            checkVersion(record, version);
            if (admin && record.photoObjectKey() == null) throw new CatchPhotoNotFoundException();
            return record;
        });
        var type = validator.validate(content, contentType);
        var key = "catches/%d/%d/%s".formatted(original.userId(), id, UUID.randomUUID());
        try {
            store.put(key, content, type.contentType());
            transaction.executeWithoutResult(status -> {
                long actorId = actor(email, admin);
                var current = record(id, actorId, admin, true);
                checkVersion(current, version);
                if (admin && current.photoObjectKey() == null) throw new CatchPhotoNotFoundException();
                persist(current, current.withPhotoObjectKey(key, clock.instant()), actorId, admin, "REPLACED");
            });
        } catch (RuntimeException failure) {
            compensate(key);
            throw conflictOrOriginal(failure);
        }
    }

    private CatchPhotoView get(String email, long id, boolean admin) {
        var current = transaction.execute(status -> record(id, actor(email, admin), admin, false));
        if (current.photoObjectKey() == null) throw new CatchPhotoNotFoundException();
        var stored = store.get(current.photoObjectKey());
        return new CatchPhotoView(stored.content(), stored.contentType(), Long.toString(current.version()));
    }

    private void remove(String email, long id, long version, boolean admin) {
        try {
            transaction.executeWithoutResult(status -> {
                long actorId = actor(email, admin);
                var current = record(id, actorId, admin, false);
                checkVersion(current, version);
                if (current.photoObjectKey() == null) return;
                persist(current, current.withoutPhoto(clock.instant()), actorId, admin, "REMOVED");
            });
        } catch (RuntimeException failure) { throw conflictOrOriginal(failure); }
    }

    private void persist(CatchRecord before, CatchRecord after, long actorId, boolean admin, String operation) {
        records.save(after); // Adapter saveAndFlush checks the actual JPA version before audit/cleanup.
        if (admin) operations.append(actorId, before.userId(), before.id(), operation, before.version(), after.updatedAt());
        if (before.photoObjectKey() != null)
            cleanup.enqueue(before.photoObjectKey(), MediaCleanupReason.valueOf(operation), after.updatedAt());
    }

    private long actor(String email, boolean admin) {
        if (!admin) return profiles.currentUser(email).id();
        var user = users.findByEmail(email).orElseThrow(() -> new AccessDeniedException("Access is denied"));
        if (user.role() != UserRole.ADMIN || user.status() != UserStatus.ACTIVE)
            throw new AccessDeniedException("Access is denied");
        return user.id();
    }

    private CatchRecord record(long id, long actorId, boolean admin, boolean afterUpload) {
        return (admin ? records.findById(id) : records.findByIdAndUserId(id, actorId))
                .orElseThrow(() -> afterUpload ? new CatchPhotoConflictException() : new CatchPhotoNotFoundException());
    }
    private static void checkVersion(CatchRecord record, long version) {
        if (version < 0) throw new InvalidPhotoVersionException();
        if (record.version() != version) throw new CatchPhotoConflictException();
    }
    private static RuntimeException conflictOrOriginal(RuntimeException failure) {
        if (failure instanceof OptimisticLockingFailureException || failure instanceof OptimisticLockException)
            return new CatchPhotoConflictException();
        return failure;
    }
    private void compensate(String key) {
        try { store.delete(key); }
        catch (RuntimeException storageFailure) {
            try {
                transaction.executeWithoutResult(status -> cleanup.enqueue(key, MediaCleanupReason.UPLOAD_ROLLBACK, clock.instant()));
            } catch (RuntimeException databaseFailure) {
                LOGGER.error("Photo upload rollback cleanup could not be persisted");
            }
        }
    }
}
