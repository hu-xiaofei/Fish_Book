# Catch Photo Media and Quality Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one optional private photo per catch record using MinIO, resilient replacement/removal cleanup, safe file validation, owner-only delivery, and the final personal-loop quality gate.

**Architecture:** Keep catch-record JSON CRUD separate from photo lifecycle. `catchlog` owns authorization and orchestration; a persistence-neutral `MediaStore` port owns byte storage; a MinIO adapter implements the port; and a MySQL cleanup-job table makes old-object deletion retryable without rolling back successful record changes. React uploads after record creation and displays photos through the same-origin protected binary endpoint.

**Tech Stack:** Java 21, Spring Boot 4.1, MinIO Java SDK 9.0.2, Spring Scheduling/Transactions, MySQL 8.4, Flyway, MinIO server pinned by `compose.yaml`, React 19, TypeScript 5.9, TanStack Query 5, Vitest/Testing Library, Playwright.

## Global Constraints

- Complete `docs/superpowers/plans/2026-08-14-catch-records-core.md` first.
- Follow `docs/superpowers/specs/2026-08-14-personal-product-loop-design.md`.
- Photos are optional; a failed upload never removes or rolls back a saved catch record.
- Allow only JPEG, PNG, and WebP up to exactly 10 MB; verify declared MIME, magic bytes, and size on the backend.
- Store only an opaque object key in `catch_records`; never return bucket, credentials, object key, or permanent public URL.
- Use unpredictable keys `catches/{userId}/{recordId}/{uuid}` without original filenames.
- Owner-check every photo operation by `(recordId, currentUserId)` and return the same 404 for missing, foreign, or absent photos on GET.
- Replace by upload-new → transactional DB swap/enqueue-old → asynchronous old delete; on DB failure, best-effort delete the new object.
- Cleanup is idempotent, retries at most 8 times, and keeps terminal `FAILED` jobs for operations review.
- Keep old backend tests independent of MinIO through a disabled fallback store; only media integration tests start a MinIO container.
- Use TDD and commit after each independently verified task.

## File and Responsibility Map

**Dependency and configuration**

- Modify `backend/pom.xml`: pin `io.minio:minio:9.0.2` and declare Testcontainers core for `GenericContainer` tests.
- Create `backend/src/main/java/com/fishbook/media/config/{MediaProperties,MinioConfiguration,MinioBucketInitializer,MediaSchedulingConfiguration}.java`.
- Modify `application.yml`, `application-local.yml`, `compose.full.yaml`, and `.env.example` for explicit private media settings.

**Media core and adapter**

- Create `backend/src/main/java/com/fishbook/media/domain/{MediaStore,StoredMedia,MediaStorageUnavailableException}.java`.
- Create `backend/src/main/java/com/fishbook/media/application/{CatchPhotoType,CatchPhotoValidator,InvalidCatchPhotoException}.java`.
- Create `backend/src/main/java/com/fishbook/media/persistence/{MinioMediaStore,DisabledMediaStore}.java`.

**Cleanup jobs**

- Create `backend/src/main/resources/db/migration/V8__create_media_cleanup_jobs.sql`.
- Create `backend/src/main/java/com/fishbook/media/cleanup/{MediaCleanupJob,MediaCleanupJobRepository,MediaCleanupService}.java` and JPA adapter files.

**Catch photo orchestration and Web**

- Extend catch repository/domain for photo-key swap/clear while preserving ownership.
- Create `backend/src/main/java/com/fishbook/catchlog/application/{CatchPhotoApplicationService,DefaultCatchPhotoApplicationService,CatchPhotoView}.java`.
- Create `backend/src/main/java/com/fishbook/catchlog/web/CatchPhotoController.java` and extend scoped error advice.

**Frontend and acceptance**

- Create `frontend/src/features/catchlog/api/catchPhotoApi.ts`.
- Create `frontend/src/features/catchlog/components/CatchPhotoPanel.tsx` and tests/styles.
- Modify catch create/detail pages for post-create optional upload and later retry/replace/remove.
- Create `e2e/tests/catch-photo-flow.spec.ts`; update README and local runbook.

---

### Task 1: Configure an Optional MinIO Adapter Without Breaking Existing Tests

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/fishbook/media/config/MediaProperties.java`
- Create: `backend/src/main/java/com/fishbook/media/config/MinioConfiguration.java`
- Create: `backend/src/main/java/com/fishbook/media/config/MinioBucketInitializer.java`
- Create: `backend/src/main/java/com/fishbook/media/domain/MediaStore.java`
- Create: `backend/src/main/java/com/fishbook/media/domain/StoredMedia.java`
- Create: `backend/src/main/java/com/fishbook/media/domain/MediaStorageUnavailableException.java`
- Create: `backend/src/main/java/com/fishbook/media/persistence/DisabledMediaStore.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-local.yml`
- Modify: `compose.full.yaml`
- Modify: `.env.example`
- Create: `backend/src/test/java/com/fishbook/media/config/MediaConfigurationTest.java`

**Interfaces:**
- Produces validated `MediaProperties(enabled, endpoint, accessKey, secretKey, bucket)`.
- Produces the media port, an unavailable fallback when disabled, and a `MinioClient` when enabled; Task 2 adds the enabled `MediaStore` adapter.

- [ ] **Step 1: Write failing configuration-context tests**

Use `ApplicationContextRunner` to assert media disabled creates `DisabledMediaStore` and no `MinioClient`; valid enabled properties create a `MinioClient`; enabled mode with blank endpoint, credentials, or bucket fails binding. Do not contact a real server in this test.

- [ ] **Step 2: Run and verify RED**

Run: `cd backend && ./mvnw -Dtest=MediaConfigurationTest test`

Expected: compilation failure because media configuration types do not exist.

- [ ] **Step 3: Pin dependencies and add exact properties**

Add:

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>9.0.2</version>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
```

Use `@ConfigurationProperties("fishbook.media")` and a class-level validation rule that requires endpoint, credentials, and bucket only when `enabled` is true. In base `application.yml`, set `enabled: false` and multipart `max-file-size: 10MB`, `max-request-size: 11MB`. In local profile, set enabled true with environment-backed endpoint, credentials, and bucket.

- [ ] **Step 4: Wire Compose to the existing MinIO service**

Add backend environment values:

```yaml
FISHBOOK_MEDIA_ENABLED: "true"
FISHBOOK_MEDIA_ENDPOINT: http://minio:9000
FISHBOOK_MEDIA_ACCESS_KEY: ${MINIO_ROOT_USER}
FISHBOOK_MEDIA_SECRET_KEY: ${MINIO_ROOT_PASSWORD}
FISHBOOK_MEDIA_BUCKET: ${MINIO_BUCKET}
```

Add `minio: condition: service_healthy` under backend `depends_on`. Keep `.env.example` bucket `fishbook-local` and do not add secrets to Git.

- [ ] **Step 5: Implement conditional client, fallback, and bucket initialization**

Build the client with:

```java
MinioClient.builder()
        .endpoint(properties.endpoint())
        .credentials(properties.accessKey(), properties.secretKey())
        .build();
```

`MinioBucketInitializer` is conditional on enabled media and uses `bucketExists` then `makeBucket`; any startup failure must stop the local full stack rather than silently expose a broken upload feature. Define the `MediaStore`/`StoredMedia` port types now so `DisabledMediaStore` compiles; every disabled-store operation throws `MediaStorageUnavailableException`.

```java
public interface MediaStore {
    void put(String objectKey, byte[] content, String contentType);
    StoredMedia get(String objectKey);
    void delete(String objectKey);
}

public record StoredMedia(byte[] content, String contentType) {
    public StoredMedia {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
```

- [ ] **Step 6: Verify configuration and old context tests**

Run:

`cd backend && ./mvnw -Dtest=MediaConfigurationTest,HealthEndpointTest,AuthFlowIntegrationTest,FishCatalogApiIntegrationTest test`

Expected: selected tests pass without requiring MinIO for the three existing contexts.

- [ ] **Step 7: Commit configuration**

```bash
git add backend/pom.xml backend/src/main/java/com/fishbook/media/config backend/src/main/java/com/fishbook/media/domain backend/src/main/java/com/fishbook/media/persistence/DisabledMediaStore.java backend/src/main/resources/application.yml backend/src/main/resources/application-local.yml backend/src/test/java/com/fishbook/media/config compose.full.yaml .env.example
git commit -m "feat: configure private media storage"
```

### Task 2: Validate Photo Bytes and Implement the MinIO Store

**Files:**
- Create: `backend/src/main/java/com/fishbook/media/application/CatchPhotoType.java`
- Create: `backend/src/main/java/com/fishbook/media/application/CatchPhotoValidator.java`
- Create: `backend/src/main/java/com/fishbook/media/application/InvalidCatchPhotoException.java`
- Create: `backend/src/main/java/com/fishbook/media/persistence/MinioMediaStore.java`
- Create: `backend/src/test/java/com/fishbook/media/application/CatchPhotoValidatorTest.java`
- Create: `backend/src/test/java/com/fishbook/media/persistence/MinioMediaStoreIntegrationTest.java`

**Interfaces:**
- Produces: `MediaStore.put`, `get`, and idempotent `delete`.
- Produces: validated `CatchPhotoType` with canonical MIME.

- [ ] **Step 1: Write failing magic-byte validation tests**

Use byte fixtures for JPEG `FF D8 FF`, full PNG eight-byte signature, and WebP `RIFF....WEBP`. Assert valid declared/actual matches pass; mismatches, empty content, unsupported GIF, and byte arrays above `10 * 1024 * 1024` raise `InvalidCatchPhotoException` code `INVALID_CATCH_PHOTO`.

- [ ] **Step 2: Run validator test and verify RED**

Run: `cd backend && ./mvnw -Dtest=CatchPhotoValidatorTest test`

Expected: compilation failure because validator types do not exist.

- [ ] **Step 3: Implement the validator against the exact Task 1 media port**

```java
public interface MediaStore {
    void put(String objectKey, byte[] content, String contentType);
    StoredMedia get(String objectKey);
    void delete(String objectKey);
}

public record StoredMedia(byte[] content, String contentType) {
    public StoredMedia { content = content.clone(); }
    @Override public byte[] content() { return content.clone(); }
}
```

Never trust filename extensions. Normalize only `image/jpeg`, `image/png`, and `image/webp`; compare the declared MIME to detected magic bytes and return the canonical type.

- [ ] **Step 4: Write the failing MinIO integration test**

Start the exact server image from `compose.yaml` with Testcontainers `GenericContainer`, readiness path `/minio/health/ready`, and dynamic media properties. Assert put/get preserves bytes and content type, delete twice succeeds, and a stopped container maps SDK/network exceptions to `MediaStorageUnavailableException` without leaking endpoint or credentials.

- [ ] **Step 5: Implement MinIO adapter and verify GREEN**

Use `PutObjectArgs`, `GetObjectArgs`, `StatObjectArgs`, and `RemoveObjectArgs`. Treat only SDK “NoSuchKey/NoSuchObject” deletion responses as idempotent success; map other checked/transport failures to the stable storage exception.

Run:

`cd backend && ./mvnw -Dtest=CatchPhotoValidatorTest,MinioMediaStoreIntegrationTest test`

Expected: both tests pass.

- [ ] **Step 6: Commit validator and adapter**

```bash
git add backend/src/main/java/com/fishbook/media backend/src/test/java/com/fishbook/media
git commit -m "feat: validate and store catch photos"
```

### Task 3: Add Durable, Bounded Media Cleanup Jobs

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__create_media_cleanup_jobs.sql`
- Create: `backend/src/main/java/com/fishbook/media/cleanup/MediaCleanupReason.java`
- Create: `backend/src/main/java/com/fishbook/media/cleanup/MediaCleanupStatus.java`
- Create: `backend/src/main/java/com/fishbook/media/cleanup/MediaCleanupJob.java`
- Create: `backend/src/main/java/com/fishbook/media/cleanup/MediaCleanupJobRepository.java`
- Create: `backend/src/main/java/com/fishbook/media/cleanup/MediaCleanupJobJpaEntity.java`
- Create: `backend/src/main/java/com/fishbook/media/cleanup/SpringDataMediaCleanupJobJpaRepository.java`
- Create: `backend/src/main/java/com/fishbook/media/cleanup/JpaMediaCleanupJobRepositoryAdapter.java`
- Create: `backend/src/main/java/com/fishbook/media/cleanup/MediaCleanupService.java`
- Create: `backend/src/main/java/com/fishbook/media/config/MediaSchedulingConfiguration.java`
- Create: `backend/src/test/java/com/fishbook/media/cleanup/MediaCleanupDatabaseMigrationTest.java`
- Create: `backend/src/test/java/com/fishbook/media/cleanup/MediaCleanupServiceTest.java`

**Interfaces:**
- Produces: transactional `enqueue(objectKey, reason, now)` and scheduled `processDueJobs()`.
- Contract: success or missing object deletes the row; failure retries 8 times then keeps a `FAILED` row.

- [ ] **Step 1: Write the failing migration test**

Assert table, allowed reason/status checks, due index `(status, next_attempt_at, id)`, nullable attempt timestamps, and default-independent explicit inserts.

- [ ] **Step 2: Create V8**

```sql
CREATE TABLE media_cleanup_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    object_key VARCHAR(512) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL,
    next_attempt_at TIMESTAMP(6) NULL,
    last_attempt_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_media_cleanup_jobs PRIMARY KEY (id),
    CONSTRAINT ck_media_cleanup_reason CHECK
        (reason IN ('REPLACED', 'REMOVED', 'RECORD_DELETED')),
    CONSTRAINT ck_media_cleanup_status CHECK (status IN ('PENDING', 'FAILED')),
    CONSTRAINT ck_media_cleanup_attempt_count CHECK (attempt_count BETWEEN 0 AND 8),
    INDEX ix_media_cleanup_due (status, next_attempt_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- [ ] **Step 3: Write failing retry-state tests**

With a fixed clock and fake `MediaStore`, assert enqueue starts pending at now; success removes the job; failures schedule 1, 2, 4, 8, 16, 32, 64 minute delays; the eighth failure sets `FAILED` and `nextAttemptAt = null`; already failed jobs are not selected; at most 20 due jobs run per tick.

- [ ] **Step 4: Implement repository and scheduler**

Define:

```java
public interface MediaCleanupJobRepository {
    MediaCleanupJob enqueue(String objectKey, MediaCleanupReason reason, Instant now);
    List<MediaCleanupJob> findDue(Instant now, int limit);
    void save(MediaCleanupJob job);
    void delete(long jobId);
}
```

Run `processDueJobs` with `@Scheduled(fixedDelayString = "${fishbook.media.cleanup-delay:PT1M}")`. Log only job ID, reason, attempt count, and request-independent error class; do not log credentials or private record fields.

- [ ] **Step 5: Verify GREEN**

Run:

`cd backend && ./mvnw -Dtest=MediaCleanupDatabaseMigrationTest,MediaCleanupServiceTest test`

Expected: migration and retry tests pass.

- [ ] **Step 6: Commit durable cleanup**

```bash
git add backend/src/main/resources/db/migration/V8__create_media_cleanup_jobs.sql backend/src/main/java/com/fishbook/media/cleanup backend/src/main/java/com/fishbook/media/config/MediaSchedulingConfiguration.java backend/src/test/java/com/fishbook/media/cleanup
git commit -m "feat: retry media object cleanup"
```

### Task 4: Implement Owner-Only Catch Photo Lifecycle and Binary API

**Files:**
- Modify: `backend/src/main/java/com/fishbook/catchlog/domain/CatchRecord.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/domain/CatchRecordRepository.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/persistence/JpaCatchRecordRepositoryAdapter.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/application/DefaultCatchRecordApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchPhotoView.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchPhotoNotFoundException.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchPhotoApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/DefaultCatchPhotoApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/web/CatchPhotoController.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/web/CatchLogExceptionHandler.java`
- Create: `backend/src/test/java/com/fishbook/catchlog/application/DefaultCatchPhotoApplicationServiceTest.java`
- Create: `backend/src/test/java/com/fishbook/catchlog/web/CatchPhotoApiIntegrationTest.java`
- Modify: `backend/src/test/java/com/fishbook/catchlog/web/CatchRecordAuthorizationTest.java`

**Interfaces:**
- Produces: PUT/GET/DELETE `/api/v1/catches/{id}/photo`.
- Produces: 400 `INVALID_CATCH_PHOTO`, 404 `CATCH_PHOTO_NOT_FOUND`, and 503 `MEDIA_STORAGE_UNAVAILABLE`.

- [ ] **Step 1: Write failing lifecycle application tests**

Assert upload validates before storage, key matches `catches/{userId}/{recordId}/{uuid}`, DB stores only the key, replacement enqueues old key as `REPLACED`, remove clears key and enqueues `REMOVED`, catch deletion enqueues `RECORD_DELETED`, foreign/missing records never call MediaStore, DB swap failure best-effort deletes the new key, and storage failure leaves the old key unchanged.

- [ ] **Step 2: Run and verify RED**

Run: `cd backend && ./mvnw -Dtest=DefaultCatchPhotoApplicationServiceTest test`

Expected: missing photo application types/methods.

- [ ] **Step 3: Implement exact application API and transaction sequence**

```java
public interface CatchPhotoApplicationService {
    void put(String authenticatedEmail, long recordId, byte[] content, String declaredContentType);
    CatchPhotoView get(String authenticatedEmail, long recordId);
    void remove(String authenticatedEmail, long recordId);
}

public record CatchPhotoView(byte[] content, String contentType) {}
```

Use `TransactionTemplate` for the DB swap plus cleanup enqueue. Re-read ownership inside the transaction. Add domain methods `withPhotoObjectKey(key, now)` and `withoutPhoto(now)` rather than exposing setters. Extend catch-record deletion from Plan 2 to enqueue `RECORD_DELETED` within the same transaction when a photo key exists.

- [ ] **Step 4: Write failing HTTP/security tests**

Cover valid JPEG/PNG/WebP, mismatched MIME/signature, GIF, >10 MB multipart, owner GET binary headers/body, no-photo 404, foreign-photo 404, idempotent owner DELETE without a photo, CSRF on PUT/DELETE, and unavailable store 503. Assert GET sends `Content-Type`, `Content-Disposition: inline`, `X-Content-Type-Options: nosniff`, and `Cache-Control: private`.

- [ ] **Step 5: Implement controller and scoped error mappings**

Controller accepts only multipart field `photo`; reject missing or empty files. Add `MaxUploadSizeExceededException` to the scoped catch advice and return `INVALID_CATCH_PHOTO`; map `CatchPhotoNotFoundException` to HTTP 404 code `CATCH_PHOTO_NOT_FOUND` and `MediaStorageUnavailableException` to HTTP 503 code `MEDIA_STORAGE_UNAVAILABLE`. Binary GET uses `ResponseEntity<byte[]>`; no object key appears in headers or JSON.

- [ ] **Step 6: Verify focused and catch regression tests**

Run:

`cd backend && ./mvnw -Dtest=DefaultCatchPhotoApplicationServiceTest,CatchPhotoApiIntegrationTest,CatchRecordAuthorizationTest,CatchRecordApiIntegrationTest test`

Expected: all selected tests pass.

- [ ] **Step 7: Commit photo lifecycle**

```bash
git add backend/src/main/java/com/fishbook/catchlog backend/src/test/java/com/fishbook/catchlog
git commit -m "feat: manage private catch photos"
```

### Task 5: Add Optional Upload, Retry, Replace, and Remove UI

**Files:**
- Create: `frontend/src/features/catchlog/api/catchPhotoApi.ts`
- Create: `frontend/src/features/catchlog/api/catchPhotoApi.test.ts`
- Create: `frontend/src/features/catchlog/components/CatchPhotoPanel.tsx`
- Create: `frontend/src/features/catchlog/components/CatchPhotoPanel.test.tsx`
- Create: `frontend/src/features/catchlog/components/CatchPhotoPanel.module.css`
- Modify: `frontend/src/features/catchlog/pages/CatchCreatePage.tsx`
- Modify: `frontend/src/features/catchlog/pages/CatchCreatePage.test.tsx`
- Modify: `frontend/src/features/catchlog/pages/CatchDetailPage.tsx`
- Modify: `frontend/src/features/catchlog/pages/CatchDetailPage.test.tsx`

**Interfaces:**
- Produces: `putCatchPhoto(recordId, file)`, `removeCatchPhoto(recordId)`, and `catchPhotoUrl(recordId)`.
- Produces: accessible optional photo panel on catch detail.

- [ ] **Step 1: Write failing API tests**

Assert `FormData` uses field `photo`, PUT and DELETE paths encode record ID, `apiFetch` does not force JSON Content-Type for FormData, and URL is `/api/v1/catches/{id}/photo`.

- [ ] **Step 2: Write failing component/page tests**

Cover client precheck for JPEG/PNG/WebP and 10 MB, upload pending, success invalidation, safe failure/retry, image error fallback, replace, remove confirmation, and no-photo state. Create-page test must prove record creation succeeds first; if optional photo upload then fails, it displays “记录已保存，照片未上传” and links to the saved detail for retry.

- [ ] **Step 3: Run and verify RED**

Run: `cd frontend && npm test -- src/features/catchlog/api/catchPhotoApi.test.ts src/features/catchlog/components/CatchPhotoPanel.test.tsx src/features/catchlog/pages/CatchCreatePage.test.tsx src/features/catchlog/pages/CatchDetailPage.test.tsx`

Expected: missing API/component assertions fail.

- [ ] **Step 4: Implement optional two-request UX**

Use `FormData`, never base64. Render `<img src={catchPhotoUrl(id)}>` only when the Plan 2 detail response has `hasPhoto: true`; no API view ever exposes an object key. Keep file input optional. After successful create, upload only if selected; on failure keep the new record ID and allow immediate retry or navigation.

- [ ] **Step 5: Verify frontend completion checks**

Run: `cd frontend && npm test && npm run lint && npm run build`

Expected: all frontend tests, lint, and build pass.

- [ ] **Step 6: Commit photo UI**

```bash
git add frontend/src/features/catchlog
git commit -m "feat: add optional catch photos"
```

### Task 6: Prove MinIO Browser Flow and Close the Personal-Loop Milestone

**Files:**
- Create: `e2e/tests/catch-photo-flow.spec.ts`
- Modify: `README.md`
- Modify: `docs/runbooks/local-development.md`
- Modify: `.github/workflows/ci.yml` only if the existing full Compose job does not already pass MinIO variables from `.env`.

**Interfaces:**
- Produces: browser evidence, operational instructions, and the final five-week phase gate.

- [ ] **Step 1: Write failing Playwright photo flow**

Register/login, create an 乌鳢 record, upload existing fixture `frontend/public/images/fish/channa-argus.jpg`, assert owner-only image response is JPEG, reload and see it, replace with another existing catalog JPEG, remove it, and assert “暂无照片”. In a second isolated context, assert the photo URL returns 404 and never returns bytes.

- [ ] **Step 2: Start the full stack and run fresh browser acceptance**

Run:

```bash
docker compose -f compose.yaml -f compose.full.yaml up -d --build
cd e2e && npm test -- catch-photo-flow.spec.ts
```

Expected after Tasks 1–5: the scenario passes. If it fails, preserve the exact failing assertion, invoke `superpowers:systematic-debugging`, and add a focused media, API, or component regression test before changing production code.

- [ ] **Step 3: Update user and operator documentation**

README: state one optional private photo per catch and MinIO is active for personal media. Runbook: document endpoint/bucket variables, bucket-private expectation, startup failure, cleanup `PENDING`/`FAILED` inspection queries, retry meaning, and orphan-object comparison procedure. Do not document credentials beyond `.env.example` placeholders.

- [ ] **Step 4: Run fresh full verification**

```bash
cd backend && ./mvnw test
cd ../frontend && npm run lint && npm test && npm run build
cd ../e2e && npm test
cd .. && docker compose --env-file .env.example -f compose.yaml -f compose.full.yaml config --quiet
docker compose -f compose.yaml -f compose.full.yaml ps
```

Expected: test/lint/build/config commands exit 0; MySQL, MinIO, backend, and frontend are running, and health-checked services report healthy.

- [ ] **Step 5: Inspect durable cleanup behavior**

Replace then remove a test photo and query:

```sql
SELECT status, attempt_count, next_attempt_at
FROM media_cleanup_jobs
ORDER BY id DESC;
```

Expected: jobs are removed after successful MinIO deletion; deliberately unavailable MinIO leaves `PENDING` rows and never restores a user-visible photo reference.

- [ ] **Step 6: Commit quality closure**

```bash
git add e2e/tests/catch-photo-flow.spec.ts README.md docs/runbooks/local-development.md .github/workflows/ci.yml
git commit -m "test: verify personal catch photo loop"
```

## Plan 3 Completion Gate

- All fresh verification commands in Task 6 exit 0.
- `git status --short` is empty.
- No API response or log exposes object keys, MinIO credentials, or private record text.
- Record creation remains successful when optional upload fails.
- Foreign users receive 404 and no image bytes.
- Cleanup success removes jobs; bounded failures remain inspectable as `FAILED` after eight attempts.
- README and runbook describe the implemented state, not future admin or cloud deployment.

## Primary Dependency Reference

- Maven Central `io.minio:minio:9.0.2`: `https://central.sonatype.com/artifact/io.minio/minio`
