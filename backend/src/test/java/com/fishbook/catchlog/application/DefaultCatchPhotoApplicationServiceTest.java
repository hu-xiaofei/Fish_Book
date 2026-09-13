package com.fishbook.catchlog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fishbook.catchlog.domain.CatchRecord;
import com.fishbook.catchlog.domain.CatchRecordDetails;
import com.fishbook.catchlog.domain.CatchRecordPage;
import com.fishbook.catchlog.domain.CatchRecordRepository;
import com.fishbook.identity.application.ProfileApplicationService;
import com.fishbook.identity.application.UserView;
import com.fishbook.media.application.CatchPhotoValidator;
import com.fishbook.media.cleanup.MediaCleanupJob;
import com.fishbook.media.cleanup.MediaCleanupJobRepository;
import com.fishbook.media.cleanup.MediaCleanupReason;
import com.fishbook.media.cleanup.MediaCleanupService;
import com.fishbook.media.domain.MediaStorageUnavailableException;
import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.domain.StoredMedia;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class DefaultCatchPhotoApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T02:00:00Z");
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};

    private RecordingCatchRecordRepository records;
    private RecordingMediaStore mediaStore;
    private RecordingCleanupRepository cleanupJobs;
    private CatchPhotoApplicationService service;

    @BeforeEach
    void setUp() {
        records = new RecordingCatchRecordRepository(record(null));
        mediaStore = new RecordingMediaStore();
        cleanupJobs = new RecordingCleanupRepository();
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new DefaultCatchPhotoApplicationService(
                new FixedProfileService(),
                records,
                new CatchPhotoValidator(),
                mediaStore,
                new MediaCleanupService(cleanupJobs, mediaStore, clock),
                clock,
                transactionTemplate(),
                org.mockito.Mockito.mock(com.fishbook.identity.domain.UserRepository.class),
                org.mockito.Mockito.mock(com.fishbook.administration.photos.application.AdminPhotoOperationRepository.class));
    }

    @Test
    void storesValidatedBytesUnderAnOwnerScopedOpaqueKeyAndPersistsOnlyTheKey() {
        service.put("angler@example.com", 31L, JPEG, "image/jpeg", 0);

        assertThat(mediaStore.putKeys).singleElement()
                .asString()
                .matches("catches/41/31/[0-9a-f-]{36}");
        assertThat(mediaStore.putContents).singleElement().isEqualTo(JPEG);
        assertThat(mediaStore.putContentTypes).containsExactly("image/jpeg");
        assertThat(records.current.photoObjectKey()).isEqualTo(mediaStore.putKeys.getFirst());
        assertThat(records.current.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsInvalidContentBeforeWritingToStorageOrTheDatabase() {
        assertThatThrownBy(() -> service.put(
                "angler@example.com", 31L, new byte[] {'G', 'I', 'F'}, "image/gif", 0))
                .isInstanceOf(com.fishbook.media.application.InvalidCatchPhotoException.class);

        assertThat(mediaStore.putKeys).isEmpty();
        assertThat(records.saveCount).isZero();
    }

    @Test
    void replacingAPhotoSwapsTheReferenceAndEnqueuesTheOldObject() {
        records.current = record("catches/41/31/old");

        service.put("angler@example.com", 31L, JPEG, "image/jpeg", 0);

        assertThat(records.current.photoObjectKey()).isNotEqualTo("catches/41/31/old");
        assertThat(cleanupJobs.enqueued).singleElement().satisfies(job -> {
            assertThat(job.objectKey()).isEqualTo("catches/41/31/old");
            assertThat(job.reason()).isEqualTo(MediaCleanupReason.REPLACED);
            assertThat(job.createdAt()).isEqualTo(NOW);
        });
    }

    @Test
    void getsOwnedPhotoBytesWithoutExposingItsObjectKey() {
        records.current = record("catches/41/31/current");
        mediaStore.stored = new StoredMedia(JPEG, "image/jpeg");

        CatchPhotoView view = service.get("angler@example.com", 31L);

        assertThat(view.content()).isEqualTo(JPEG);
        assertThat(view.contentType()).isEqualTo("image/jpeg");
        assertThat(mediaStore.getKeys).containsExactly("catches/41/31/current");
    }

    @Test
    void removalClearsTheReferenceAndEnqueuesCleanupButNoPhotoIsIdempotent() {
        records.current = record("catches/41/31/current");

        service.remove("angler@example.com", 31L, 0);
        service.remove("angler@example.com", 31L, 1);

        assertThat(records.current.photoObjectKey()).isNull();
        assertThat(records.current.updatedAt()).isEqualTo(NOW);
        assertThat(cleanupJobs.enqueued).singleElement().satisfies(job -> {
            assertThat(job.objectKey()).isEqualTo("catches/41/31/current");
            assertThat(job.reason()).isEqualTo(MediaCleanupReason.REMOVED);
        });
    }

    @Test
    void missingOrForeignRecordsUsePhotoNotFoundWithoutCallingStorage() {
        records.visible = false;

        assertThatThrownBy(() -> service.put("angler@example.com", 31L, JPEG, "image/jpeg", 0))
                .isInstanceOf(CatchPhotoNotFoundException.class);
        assertThatThrownBy(() -> service.get("angler@example.com", 31L))
                .isInstanceOf(CatchPhotoNotFoundException.class);
        assertThatThrownBy(() -> service.remove("angler@example.com", 31L, 0))
                .isInstanceOf(CatchPhotoNotFoundException.class);

        assertThat(mediaStore.putKeys).isEmpty();
        assertThat(mediaStore.getKeys).isEmpty();
        assertThat(mediaStore.deletedKeys).isEmpty();
    }

    @Test
    void databaseSwapFailureBestEffortDeletesTheNewObjectAndPreservesTheOldReference() {
        records.current = record("catches/41/31/old");
        records.failSave = true;

        assertThatThrownBy(() -> service.put("angler@example.com", 31L, JPEG, "image/jpeg", 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        assertThat(records.current.photoObjectKey()).isEqualTo("catches/41/31/old");
        assertThat(mediaStore.deletedKeys).containsExactly(mediaStore.putKeys.getFirst());
        assertThat(cleanupJobs.enqueued).isEmpty();
    }

    @Test
    void storageFailureLeavesTheOldReferenceUnchanged() {
        records.current = record("catches/41/31/old");
        mediaStore.failPut = true;

        assertThatThrownBy(() -> service.put("angler@example.com", 31L, JPEG, "image/jpeg", 0))
                .isInstanceOf(MediaStorageUnavailableException.class);

        assertThat(records.current.photoObjectKey()).isEqualTo("catches/41/31/old");
        assertThat(records.saveCount).isZero();
        assertThat(cleanupJobs.enqueued).isEmpty();
        assertThat(mediaStore.deletedKeys).containsExactly(mediaStore.putKeys.getFirst());
    }

    private static CatchRecord record(String photoObjectKey) {
        return CatchRecord.restore(
                31L,
                41L,
                new CatchRecordDetails(
                        7L,
                        LocalDate.parse("2026-08-20"),
                        "城郊水库",
                        new BigDecimal("42.5"),
                        new BigDecimal("1350"),
                        "路亚",
                        "傍晚近岸中鱼"),
                photoObjectKey,
                Instant.parse("2026-08-19T12:00:00Z"),
                Instant.parse("2026-08-19T12:00:00Z"));
    }

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {}

            @Override
            public void rollback(TransactionStatus status) {}
        });
    }

    private static final class FixedProfileService implements ProfileApplicationService {
        @Override
        public UserView currentUser(String normalizedEmail) {
            return new UserView(41L, normalizedEmail, "angler", "USER");
        }

        @Override
        public UserView updateNickname(String normalizedEmail, String nickname) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingCatchRecordRepository implements CatchRecordRepository {
        private CatchRecord current;
        private boolean visible = true;
        private boolean failSave;
        private int saveCount;

        private RecordingCatchRecordRepository(CatchRecord current) {
            this.current = current;
        }

        @Override
        public CatchRecord save(CatchRecord record) {
            if (failSave) {
                throw new IllegalStateException("database unavailable");
            }
            saveCount++;
            current = CatchRecord.restore(record.id(), record.userId(), record.details(), record.photoObjectKey(),
                    record.createdAt(), record.updatedAt(), record.version() + 1);
            return current;
        }

        @Override
        public Optional<CatchRecord> findByIdAndUserId(long id, long userId) {
            return visible && current.id() == id && current.userId() == userId
                    ? Optional.of(current)
                    : Optional.empty();
        }

        @Override
        public Optional<CatchRecord> findById(long id) {
            return current.id() == id ? Optional.of(current) : Optional.empty();
        }

        @Override
        public CatchRecordPage findByUserId(long userId, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteByIdAndUserId(long id, long userId, long version) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingMediaStore implements MediaStore {
        private final List<String> putKeys = new ArrayList<>();
        private final List<byte[]> putContents = new ArrayList<>();
        private final List<String> putContentTypes = new ArrayList<>();
        private final List<String> getKeys = new ArrayList<>();
        private final List<String> deletedKeys = new ArrayList<>();
        private StoredMedia stored = new StoredMedia(JPEG, "image/jpeg");
        private boolean failPut;

        @Override
        public void put(String objectKey, byte[] content, String contentType) {
            putKeys.add(objectKey);
            putContents.add(content.clone());
            putContentTypes.add(contentType);
            if (failPut) {
                throw new MediaStorageUnavailableException();
            }
        }

        @Override
        public StoredMedia get(String objectKey) {
            getKeys.add(objectKey);
            return stored;
        }

        @Override
        public void delete(String objectKey) {
            deletedKeys.add(objectKey);
        }
    }

    private static final class RecordingCleanupRepository implements MediaCleanupJobRepository {
        private final List<MediaCleanupJob> enqueued = new ArrayList<>();

        @Override
        public MediaCleanupJob enqueue(String objectKey, MediaCleanupReason reason, Instant now) {
            var job = new MediaCleanupJob(
                    (long) enqueued.size() + 1,
                    objectKey,
                    reason,
                    com.fishbook.media.cleanup.MediaCleanupStatus.PENDING,
                    0,
                    now,
                    null,
                    now);
            enqueued.add(job);
            return job;
        }

        @Override
        public List<MediaCleanupJob> findDue(Instant now, int limit) {
            return List.of();
        }

        @Override
        public void save(MediaCleanupJob job) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(long jobId) {
            throw new UnsupportedOperationException();
        }
    }
}
