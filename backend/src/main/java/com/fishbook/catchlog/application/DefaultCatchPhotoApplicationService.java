package com.fishbook.catchlog.application;

import com.fishbook.catchlog.domain.CatchRecord;
import com.fishbook.catchlog.domain.CatchRecordRepository;
import com.fishbook.identity.application.ProfileApplicationService;
import com.fishbook.media.application.CatchPhotoValidator;
import com.fishbook.media.cleanup.MediaCleanupReason;
import com.fishbook.media.cleanup.MediaCleanupService;
import com.fishbook.media.domain.MediaStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DefaultCatchPhotoApplicationService implements CatchPhotoApplicationService {
    private final ProfileApplicationService profileApplicationService;
    private final CatchRecordRepository catchRecordRepository;
    private final CatchPhotoValidator validator;
    private final MediaStore mediaStore;
    private final MediaCleanupService cleanupService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public DefaultCatchPhotoApplicationService(
            ProfileApplicationService profileApplicationService,
            CatchRecordRepository catchRecordRepository,
            CatchPhotoValidator validator,
            MediaStore mediaStore,
            MediaCleanupService cleanupService,
            Clock clock,
            TransactionTemplate transactionTemplate) {
        this.profileApplicationService = Objects.requireNonNull(profileApplicationService);
        this.catchRecordRepository = Objects.requireNonNull(catchRecordRepository);
        this.validator = Objects.requireNonNull(validator);
        this.mediaStore = Objects.requireNonNull(mediaStore);
        this.cleanupService = Objects.requireNonNull(cleanupService);
        this.clock = Objects.requireNonNull(clock);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
    }

    @Override
    public void put(
            String authenticatedEmail,
            long recordId,
            byte[] content,
            String declaredContentType) {
        var user = profileApplicationService.currentUser(authenticatedEmail);
        ownedRecord(recordId, user.id());
        var photoType = validator.validate(content, declaredContentType);
        var newObjectKey = objectKey(user.id(), recordId);

        mediaStore.put(newObjectKey, content, photoType.contentType());
        try {
            transactionTemplate.executeWithoutResult(status -> {
                var current = ownedRecord(recordId, user.id());
                var now = clock.instant();
                catchRecordRepository.save(current.withPhotoObjectKey(newObjectKey, now));
                if (current.photoObjectKey() != null) {
                    cleanupService.enqueue(
                            current.photoObjectKey(), MediaCleanupReason.REPLACED, now);
                }
            });
        } catch (RuntimeException exception) {
            bestEffortDelete(newObjectKey);
            throw exception;
        }
    }

    @Override
    public CatchPhotoView get(String authenticatedEmail, long recordId) {
        var user = profileApplicationService.currentUser(authenticatedEmail);
        var record = ownedRecord(recordId, user.id());
        if (record.photoObjectKey() == null) {
            throw new CatchPhotoNotFoundException();
        }
        var stored = mediaStore.get(record.photoObjectKey());
        return new CatchPhotoView(stored.content(), stored.contentType());
    }

    @Override
    public void remove(String authenticatedEmail, long recordId) {
        var user = profileApplicationService.currentUser(authenticatedEmail);
        transactionTemplate.executeWithoutResult(status -> {
            var current = ownedRecord(recordId, user.id());
            if (current.photoObjectKey() == null) {
                return;
            }
            var now = clock.instant();
            catchRecordRepository.save(current.withoutPhoto(now));
            cleanupService.enqueue(current.photoObjectKey(), MediaCleanupReason.REMOVED, now);
        });
    }

    private CatchRecord ownedRecord(long recordId, long userId) {
        return catchRecordRepository.findByIdAndUserId(recordId, userId)
                .orElseThrow(CatchPhotoNotFoundException::new);
    }

    private static String objectKey(long userId, long recordId) {
        return "catches/%d/%d/%s".formatted(userId, recordId, UUID.randomUUID());
    }

    private void bestEffortDelete(String objectKey) {
        try {
            mediaStore.delete(objectKey);
        } catch (RuntimeException ignored) {
            // The original database error remains the actionable failure.
        }
    }
}
