# 管理员照片管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 现有管理员在私有后台查看、替换和删除所有用户照片，同时保留普通用户隔离、操作记录及并发保护。

**Architecture:** 独立 `/api/v1/admin/photos/**` 管理边界复用现有角色，原用户入口保留所有权限制；共用照片修改编排与已有清理服务。钓获记录使用 JPA 乐观锁和客户端 If-Match；管理员操作证据与业务引用原子提交。前端分开用户照片组件与管理员页面，复用会话/CSRF/缓存规则，不新增存储后端。

**Tech Stack:** Java 21、现有 Spring Boot/JPA、MySQL 8.4、React/TypeScript/TanStack Query、现有 Testcontainers/Vitest/Playwright。

**Spec:** `docs/superpowers/specs/2026-09-12-admin-photo-management-design.md`

## Global Constraints

- 复用现有 `USER`、`ADMIN`，不新增角色或第三方依赖。
- 普通用户只能读取、上传、替换和删除自己的照片；现有管理员通过独立管理接口查看、替换和删除所有用户的照片。
- OSS 和 MinIO 仍私有；不返回公开或预签名 URL、对象键、Bucket、Endpoint、凭证或 SDK 错误细节。
- 照片格式仍为 JPEG、PNG、WebP，最大 10 MiB；沿用服务端签名校验和存储大小上限。
- 写操作继续要求 Session、服务端角色/所有权校验和 CSRF；不能仅靠前端隐藏按钮授权。
- 所有照片上传、替换、删除都必须提供当前版本；旧版本冲突必须拒绝，不能自动刷新版本后重试写入。
- 成功的管理员替换/删除、照片引用更新和旧对象清理入队必须在同一数据库事务提交；事务失败不留下成功操作记录。
- 不提供旧照片恢复；旧对象继续通过清理补偿删除，不做前缀批量删除。
- 使用现有 Java 21、Spring Boot、MySQL 8.4、React、TypeScript 和 TanStack Query；不调整依赖版本。
- 本轮仅本地开发和测试，不执行云资源操作、真实数据库迁移、购买、发布、推送或合并。
- 保留用户现有文件和 MinIO 数据；已批准的学习型 RDS 内网非加密例外不扩展为浏览器或 OSS HTTPS 例外。

---

## Preparation / file boundaries

Start from `main` at `bf19545` or its verified descendant. Record actual starting SHA and unrelated changes. Execution workspace must be isolated following using-git-worktrees; existing `.worktrees/` must be checked ignored. Baseline backend command is `cd backend && ./mvnw -B test`; frontend uses the repository's Node 24 toolchain and `npm ci`, then `npm test`, `npm run build`, `npm run lint`. Do not replace a user's `.env` or start an unknown shared Compose stack. No feature implementation is complete merely because a test fixture passed.

Responsibilities:

- `catchlog/domain` and its JPA adapter own record revision; do not add administrator bypass to user-owned queries.
- `administration/photos/application` owns administrator gate, minimal metadata and admin use cases.
- `administration/photos/persistence` owns read-only metadata queries and append-only administrator operations.
- `catchlog/application` owns shared validated photo mutation, optimistic conflict translation and compensation.
- `catchlog/web/PhotoRevision.java` owns strict HTTP version parsing; both controllers consume it.
- `frontend/features/administration/photos` owns the photo management UI, API and query keys.
- Existing owner photo API/components are updated to the same unconditional version requirement; no compatibility path skips concurrency checks.

### Task 1: Versioned record persistence and atomic operation storage

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__version_catches_and_record_admin_photo_operations.sql`
- Modify: `backend/src/main/java/com/fishbook/catchlog/domain/CatchRecord.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/domain/CatchRecordRepository.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/persistence/CatchRecordJpaEntity.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/persistence/JpaCatchRecordRepositoryAdapter.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/persistence/SpringDataCatchRecordJpaRepository.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/application/DefaultCatchRecordApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchPhotoConflictException.java`
- Modify: `backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java` (fixed 409 conflict handler)
- Modify: `backend/src/main/java/com/fishbook/media/cleanup/MediaCleanupReason.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/application/AdminPhotoOperation.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/application/AdminPhotoOperationRepository.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/persistence/AdminPhotoOperationJpaEntity.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/persistence/SpringDataAdminPhotoOperationRepository.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/persistence/JpaAdminPhotoOperationRepository.java`
- Test: existing `backend/src/test/java/com/fishbook/catchlog/domain/CatchRecordTest.java`
- Test: existing `backend/src/test/java/com/fishbook/catchlog/persistence/JpaCatchRecordRepositoryAdapterTest.java`
- Create test: `backend/src/test/java/com/fishbook/administration/photos/persistence/AdminPhotoPersistenceTest.java`
- Adapt compilation: fake CatchRecordRepository implementations in existing catchlog application tests.

**Interfaces:**
- Consumes: `CatchRecordRepository.save(CatchRecord)`, existing `MediaCleanupService.enqueue(String, MediaCleanupReason, Instant)`.
- Produces: `CatchRecord.version(): Long`; `CatchRecord.restore(long,long,CatchRecordDetails,String,Instant,Instant,Long)`; preserve six-argument constructor/restore overloads for existing local test fixtures only.
- Produces: `CatchRecordRepository.findById(long): Optional<CatchRecord>` and `deleteByIdAndUserId(long,long,long): boolean`; remove unconditional delete usage in production and adapt fakes.
- Produces: `CatchPhotoConflictException` with fixed code `CATCH_PHOTO_CONFLICT`; handler returns 409 and fixed Chinese conflict explanation, without causes/details.
- Produces: `AdminPhotoOperation(long id,long actorUserId,long ownerUserId,long recordId,String operation,String previousRevision,Instant occurredAt)`.
- Produces: `AdminPhotoOperationRepository.append(long actorId,long ownerId,long recordId,String operation,long previousVersion,Instant at): void` and `findByRecordId(long recordId,int page,int size): Page<AdminPhotoOperation>`.

- [ ] **Step 1: Add database behavior regressions before production changes.** Existing record is saved, then read in two independent transactions; the first update is flushed and committed, the second stale update must throw `ObjectOptimisticLockingFailureException` and must not restore the old photo. Independent EntityManager contexts are required: clearing one shared transaction does not prove concurrent persistence behavior.

```java
var day = LocalDate.parse("2026-09-01");
var now = Instant.parse("2026-09-01T00:00:00Z");
var initial = adapter.save(recordFor(USER_ID, 1L, day, now, "测试钓点", "old"));
var stale = adapter.findByIdAndUserId(initial.id(), USER_ID).orElseThrow();
var newer = adapter.save(stale.withPhotoObjectKey("new", now.plusSeconds(1)));
assertThat(newer.version()).isGreaterThan(stale.version());
assertThat(adapter.deleteByIdAndUserId(initial.id(), USER_ID, stale.version())).isFalse();
assertThat(adapter.findByIdAndUserId(initial.id(), USER_ID)).contains(newer);
```

Add migration test from V9 seeded photo rows to V10: version remains 0, key/data unchanged; audit table valid operations enforced and `UPLOAD_ROLLBACK` cleanup accepted. Operations must survive deletion of the target catch record; no object key/body/email columns.

- [ ] **Step 2: Run focused tests and record the genuine RED.** Run `cd backend && ./mvnw -B -Dtest=CatchRecordTest,JpaCatchRecordRepositoryAdapterTest,AdminPhotoPersistenceTest test`. Compilation failures establish missing interfaces but at least one behavioral red must subsequently expose missing version protection before fixing it; sandbox or Docker setup failures are not RED evidence.

- [ ] **Step 3: Implement V10 and version-preserving domain/entity mapping.**

```sql
ALTER TABLE catch_records ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE admin_photo_operations (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor_user_id BIGINT NOT NULL,
  owner_user_id BIGINT NOT NULL,
  catch_record_id BIGINT NOT NULL,
  operation VARCHAR(16) NOT NULL,
  previous_version BIGINT NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT ck_admin_photo_operation CHECK (operation IN ('REPLACED', 'REMOVED')),
  INDEX ix_admin_photo_record_time (catch_record_id, occurred_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE media_cleanup_jobs DROP CHECK ck_media_cleanup_reason;
ALTER TABLE media_cleanup_jobs ADD CONSTRAINT ck_media_cleanup_reason CHECK
  (reason IN ('REPLACED', 'REMOVED', 'RECORD_DELETED', 'UPLOAD_ROLLBACK'));
```

Use `@Version private Long version` in CatchRecordJpaEntity; include real version in every toDomain/toEntity mapping, preserve it on domain mutations, and use `saveAndFlush` so optimistic failure occurs before side-effect bookkeeping succeeds. New domain records carry null version. Change original native delete to version-qualified delete:

```java
@Modifying
@Query("DELETE FROM CatchRecordJpaEntity r WHERE r.id = :id AND r.userId = :userId AND r.version = :version")
long deleteByIdAndUserId(long id, long userId, long version);
```

DefaultCatchRecordApplicationService.delete supplies the version read in its transaction. A zero-row concurrent delete is a conflict, not successful deletion/cleanup of an obsolete key. Existing ownership 404 behavior remains for records not found at the start. No new table foreign key cascades away audit history. JpaAdminPhotoOperationRepository uses the same transaction manager and `saveAndFlush`, not REQUIRES_NEW.

- [ ] **Step 4: Run focused GREEN and existing catch record/photo tests.** Confirm old constructors and fake repositories compile, version increments across metadata and photo saves, and stale update/delete does not alter the winner. Query actual MySQL rows rather than asserting only fake counters.
- [ ] **Step 5: Commit only Task 1 files.** `git diff --check`, explicitly stage the named Task 1 files and commit `feat: version catch records and persist admin photo operations`.

### Task 2: Authorized admin API and version-safe shared mutations

**Files:**
- Create: `backend/src/main/java/com/fishbook/administration/photos/application/AdminPhotoSummaryView.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/application/AdminPhotoPageView.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/application/AdminPhotoOperationPageView.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/application/InvalidAdminPhotoQueryException.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/application/AdminPhotoQuery.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/application/AdminPhotoQueryRepository.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/application/AdminPhotoApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/application/DefaultAdminPhotoApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/persistence/JdbcAdminPhotoQueryRepository.java`
- Create: `backend/src/main/java/com/fishbook/administration/photos/web/AdminPhotoController.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/web/PhotoRevision.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/PhotoVersionRequiredException.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/InvalidPhotoVersionException.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/application/CatchPhotoApplicationService.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/application/DefaultCatchPhotoApplicationService.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/application/CatchPhotoView.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/application/CatchRecordDetailView.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/application/DefaultCatchRecordApplicationService.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/web/CatchPhotoController.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/web/dto/CatchRecordDetailResponse.java`
- Modify: `backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/fishbook/catchlog/web/CatchPhotoApiIntegrationTest.java`
- Test: `backend/src/test/java/com/fishbook/catchlog/application/DefaultCatchPhotoApplicationServiceTest.java`
- Create test: `backend/src/test/java/com/fishbook/administration/photos/web/AdminPhotoApiIntegrationTest.java`
- Create test: `backend/src/test/java/com/fishbook/administration/photos/application/AdminPhotoConcurrencyTest.java`
- Create test: `backend/src/test/java/com/fishbook/catchlog/web/PhotoRevisionTest.java`

**Interfaces:**
- Consumes: Task 1 versioned CatchRecord/repository and AdminPhotoOperationRepository.
- Produces: `AdminPhotoSummaryView(long recordId,long ownerUserId,String ownerNickname,String commonNameZh,LocalDate caughtOn,boolean hasPhoto,String revision,Instant updatedAt)`.
- Produces: `AdminPhotoPageView(List<AdminPhotoSummaryView> items,int page,int size,long totalItems,int totalPages)`.
- Produces: `AdminPhotoOperationPageView(List<AdminPhotoOperation> items,int page,int size,long totalItems,int totalPages)`; explicitly project repository Page, do not expose internal JPA entities or pageable internals.
- Produces: `AdminPhotoQuery(Long userId,int page,int size)`; `AdminPhotoQuery.from(String userId,String page,String size)` enforces positive userId/nonnegative page/size 1..50.
- Produces: `AdminPhotoQueryRepository.search(AdminPhotoQuery): AdminPhotoPageView`, `findByRecordId(long): Optional<AdminPhotoSummaryView>`.
- Produces: owner `put(String email,long recordId,byte[] content,String contentType,long expectedVersion)` and `remove(String email,long recordId,long expectedVersion)`, existing `get(String,long)`.
- Produces: shared photo service `getForAdmin(String,long)`, `putForAdmin(String,long,byte[],String,long)`, `removeForAdmin(String,long,long)`; no unguarded public internal mutation method.
- Produces: `CatchPhotoView(byte[] content,String contentType,String revision)` and detail `revision: String`.
- Produces: `AdminPhotoApplicationService.search(String email,AdminPhotoQuery): AdminPhotoPageView`, `get(String email,long recordId): AdminPhotoSummaryView`, `operations(String email,long recordId,int page,int size): AdminPhotoOperationPageView`; all do current database ADMIN check before repository calls.
- Produces: `InvalidAdminPhotoQueryException` with fixed code `INVALID_ADMIN_PHOTO_QUERY`; dedicated generic 400 handler for invalid IDs/filter/pagination, not a leaked parse exception.
- Produces: `PhotoRevision.parse(String ifMatch): long` with fixed exceptions and strong single decimal ETag parsing.
- Produces: HTTP routes/statuses and response fields in design5, plus owner writes' mandatory If-Match.

- [ ] **Step 1: Write API and race tests.** Use MySQL-backed MockMvc with a real ACTIVE ADMIN database fixture, two USER fixtures and mocked MediaStore. Preserve `@DirtiesContext` convention. Verify every management verb denies anonymous/USER before storage or admin metadata queries; ADMIN can read a foreign photo, replace/delete it without changing record details, and produces exactly one correct operation + cleanup job.

```java
mvc.perform(get("/api/v1/admin/photos/{id}/content", id).with(user(ADMIN_EMAIL).roles("ADMIN")))
    .andExpect(status().isOk()).andExpect(content().bytes(JPEG))
    .andExpect(header().string("Cache-Control", "private, no-store"));
mvc.perform(multipart(HttpMethod.PUT, "/api/v1/admin/photos/{id}/content", id)
    .file(new MockMultipartFile("photo", "test.jpg", "image/jpeg", JPEG))
    .header("If-Match", "\"0\"").with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
    .andExpect(status().isNoContent());
assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_photo_operations WHERE catch_record_id = ? AND operation = 'REPLACED'", Integer.class, id)).isEqualTo(1);
mvc.perform(delete("/api/v1/catches/{id}/photo", id)
    .header("If-Match", "\"0\"").with(user(OWNER_EMAIL)).with(csrf()))
    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATCH_PHOTO_CONFLICT"));
```

Add opposite winner order (owner then administrator), missing version 428, malformed/wildcard/overflow 400, unchanged-version empty delete 204/no duplicate evidence, missing photo admin replace 404, CSRF rejection, invalid MIME/10 MiB bounds/no storage interactions, 503 generic privacy, page/user filtering and exact no-key/no-email/no-notes JSON exposure. Ensure an ADMIN session whose database role is USER is rejected by application layer.

Race fixture: pause admin MediaStore.put after uploading via CountDownLatch; execute owner save or delete in another real transaction; resume admin and require 409, winner reference retained, no false success operation/old-object cleanup, uploaded loser key compensated. Separately fail the audit insert/flush and cleanup enqueue to prove photo/audit/cleanup rollback; do not use a single mock assertion as transaction proof.

- [ ] **Step 2: Run focused RED.** `cd backend && ./mvnw -B -Dtest=PhotoRevisionTest,AdminPhotoApiIntegrationTest,AdminPhotoConcurrencyTest,CatchPhotoApiIntegrationTest,DefaultCatchPhotoApplicationServiceTest test`. First observe a missing route or unsafe behavior fail, then implement; capture stdout/stderr/exit in task report without real credentials.

- [ ] **Step 3: Implement strict parsing and shared guarded mutations.**

```java
static long parse(String value) {
    if (value == null) throw new PhotoVersionRequiredException();
    if (!value.matches("\"[0-9]+\"")) throw new InvalidPhotoVersionException();
    try { return Long.parseLong(value.substring(1, value.length() - 1)); }
    catch (NumberFormatException failure) { throw new InvalidPhotoVersionException(); }
}
```

Guard current database user before any arbitrary-record access. User methods choose `findByIdAndUserId`; admin methods require ADMIN and then choose `findById`. Compare supplied version before file upload and again inside the transaction; recheck active current ADMIN within the transaction before committing an admin mutation after external upload. Persist current record with its true version and `saveAndFlush`; append admin evidence + enqueue old key inside that transaction. Translate optimistic save/delete failures outside the failed transaction to fixed CatchPhotoConflictException; put compensates only the exact new UUID key. For failed compensation enqueue `UPLOAD_ROLLBACK` in a fresh usable transaction and never queue the winner or include key/exception message in ordinary logs. If both storage delete and DB enqueue fail, retain original failure and document bounded residual orphan risk.

JdbcAdminPhotoQueryRepository runs a joined, parameterized minimal projection (no notes/location/email/object key selection), count query, deterministic `updated_at DESC, id DESC`, bounded LIMIT/OFFSET. Admin service validates query and role; controller does not accept actor/owner roles in request data. Metadata detail may return hasPhoto=false after removal, content uses stable 404. Successful writes return 204; all image/private metadata responses use no-store and image GET ETag quotes the version from the same authorized record used for reading.

Add only dedicated fixed handlers: 428 `PHOTO_VERSION_REQUIRED`, 400 `INVALID_PHOTO_VERSION`, 400 `INVALID_ADMIN_PHOTO_QUERY`, and reuse Task1's 409 `CATCH_PHOTO_CONFLICT`; no exception cause/details or request headers forwarded. Reuse the existing AccessDeniedException handler and generic 503, do not refactor global unexpected-error logs. Add revision to actual create/get/update detail responses and adapt constructor-using tests. No public/signed URLs.

- [ ] **Step 4: Run focused GREEN then `./mvnw -B test`.** Read real counts; verify JPA race tests really execute concurrent transactions and rollback results. No changed test may remove the owner-isolation assertions to make admin tests pass.
- [ ] **Step 5: Commit explicitly scoped Task 2 files.** Commit `feat: add authorized version-safe administrator photo API`.

### Task 3: Owner UI version protocol and accurate privacy copy

**Files:**
- Modify: `frontend/src/features/catchlog/model/types.ts`
- Modify: `frontend/src/features/catchlog/api/catchPhotoApi.ts`
- Modify: `frontend/src/features/catchlog/components/CatchPhotoPanel.tsx`
- Modify: `frontend/src/features/catchlog/pages/CatchDetailPage.tsx`
- Modify: `frontend/src/features/catchlog/pages/CatchNewPage.tsx`
- Test: existing catchPhotoApi, CatchPhotoPanel, CatchNewPage, CatchDetailPage tests.
- Adapt: existing CatchRecordDetail test fixtures in catchlog page/API tests to include explicit revision.

**Interfaces:**
- Consumes: Task 2 owner detail `revision: string`, mandatory If-Match and conflict errors.
- Produces: `putCatchPhoto(recordId:number,file:File,revision:string):Promise<void>`; `removeCatchPhoto(recordId:number,revision:string):Promise<void>`.
- Produces: `CatchPhotoPanel` requires `revision:string` prop in addition to existing recordId/hasPhoto/photoAlt.
- Produces: detail image source uses current revision; no write compatibility default version.

- [ ] **Step 1: Write frontend RED.** Assert request revision cannot be omitted, correct quoting, and 409 does not send a second mutation. Verify create+optional-photo uses returned create revision and that a failed upload retry does not silently adopt a different current revision. Confirm new copy replaces the inaccurate owner-only promise.

```ts
await putCatchPhoto(31, new File(['jpeg'], 'test.jpg', { type: 'image/jpeg' }), '7');
const options = fetchMock.mock.calls.at(-1)?.[1];
expect(new Headers(options?.headers).get('If-Match')).toBe('"7"');
```

- [ ] **Step 2: Run focused RED.** `cd frontend && npm test -- src/features/catchlog/api/catchPhotoApi.test.ts src/features/catchlog/components/CatchPhotoPanel.test.tsx src/features/catchlog/pages/CatchNewPage.test.tsx src/features/catchlog/pages/CatchDetailPage.test.tsx`.

- [ ] **Step 3: Update owner protocol and confirmation lifecycle.**

```ts
export function putCatchPhoto(recordId: number, file: File, revision: string) {
  const form = new FormData(); form.append('photo', file);
  return apiFetch<void>(catchPhotoUrl(recordId), {
    method: 'PUT', headers: { 'If-Match': `"${revision}"` }, body: form,
  });
}
export const removeCatchPhoto = (recordId: number, revision: string) =>
  apiFetch<void>(catchPhotoUrl(recordId), { method: 'DELETE', headers: { 'If-Match': `"${revision}"` } });
```

Do not fallback missing revision to '0'. Render revision as an encoded image query parameter; keep no-store server response. Capture target ID/revision in mutation variables and removal confirmation, not a changing closure; changing target/revision cancels old confirmation. On success invalidate/refetch owner detail/list and wait for current state before enabling another write. On 409 show the fixed conflict explanation, close confirmation and refresh metadata, but require a new user action. Existing SessionGeneration guards remain on success/error paths; stale response after logout never repopulates query cache. New-page failure retry fetches current detail first; differing revision displays conflict and sends no upload until explicitly confirmed with current state.

Exact explanatory copy: `你和平台管理员可查看，管理员可因管理需要替换或移除照片。` Retain `支持 JPEG、PNG、WebP，最大 10 MB。` No claim administrator cannot see photos remains in live UI.

- [ ] **Step 4: Run focused GREEN and frontend full test/build/lint.** `npm test`, `npm run build`, `npm run lint`; include tests for simultaneous owner/detail revision change, 401 expiry and no stale mutation retry.
- [ ] **Step 5: Commit Task 3.** Commit `feat: protect owner photo actions with explicit revisions`.

### Task 4: Administrator management pages and session-scoped caches

**Files:**
- Create: `frontend/src/features/administration/photos/model/types.ts`
- Create: `frontend/src/features/administration/photos/model/adminPhotoSearchParams.ts`
- Create: `frontend/src/features/administration/photos/api/adminPhotoApi.ts`
- Create: `frontend/src/features/administration/photos/pages/AdminPhotoListPage.tsx`
- Create: `frontend/src/features/administration/photos/pages/AdminPhotoDetailPage.tsx`
- Create: `frontend/src/features/administration/photos/components/AdminPhotoActions.tsx`
- Create: `frontend/src/features/administration/photos/components/AdminPhotoOperations.tsx`
- Create: `frontend/src/features/administration/photos/pages/AdminPhotoPages.module.css`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/features/auth/components/SessionNav.tsx`
- Modify: `frontend/src/features/auth/components/AdminRoute.tsx`
- Modify: `frontend/src/features/auth/api/sessionCache.ts`
- Create tests alongside adminPhotoSearchParams, adminPhotoApi, AdminPhotoListPage, AdminPhotoDetailPage, AdminPhotoActions, AdminPhotoOperations.
- Modify tests: SessionNav, AdminRoute, sessionCache.

**Interfaces:**
- Consumes: Task 2 admin routes, AdminPhotoSummaryView and AdminPhotoOperation JSON; Task 3 owner query invalidation protocol.
- Produces: `ADMIN_PHOTOS_QUERY_KEY = ['admin-photos'] as const`.
- Produces: `AdminPhotoFilters {userId:string;page:number;size:20}`; strict parsing rejects malformed filters rather than broadening a failed filter to all users unnoticed.
- Produces: `fetchAdminPhotoPage(filters)`, `fetchAdminPhoto(recordId)`, `adminPhotoContentUrl(recordId)`, `replaceAdminPhoto(recordId,file,revision)`, `removeAdminPhoto(recordId,revision)`, `fetchAdminPhotoOperations(recordId,page)`.
- Produces: query keys including list/filter/page, detail/id, operations/id/page and page components for `/admin/photos` and `/admin/photos/:id`.

- [ ] **Step 1: Write visible behavior RED.** List tests use mocked API: shows owner ID/nickname/fish/date, submits user ID filter to URL, changes page, has no eagerly loaded images, handles empty/error/401/403. Detail tests show private content route, no-key metadata, image load fallback and operations. Actions test file preview, cancels replacement/delete without write, binds expected revision on confirm, disables in-flight controls, revokes object URLs, and refuses auto retry on409.

```tsx
await user.upload(screen.getByLabelText('新照片'), file);
await user.click(screen.getByRole('button', { name: '替换照片' }));
expect(screen.getByRole('alertdialog', { name: '确认替换照片' })).toBeVisible();
expect(replaceAdminPhoto).not.toHaveBeenCalled();
await user.click(screen.getByRole('button', { name: '确认替换' }));
expect(replaceAdminPhoto).toHaveBeenCalledWith(31, file, '7');
```

Test revision changes while confirmation is open closes it and does not write; unmount, target switch,401 and403 release preview URL/selection. SessionCache test seeds admin-photo/detail/operation queries and proves logout clears all plus existing private domains. A late successful write after sessionGeneration changes must not repopulate any private query.

- [ ] **Step 2: Run focused RED.** `cd frontend && npm test -- src/features/administration/photos src/features/auth/api/sessionCache.test.ts src/features/auth/components/SessionNav.test.tsx src/features/auth/components/AdminRoute.test.tsx`.

- [ ] **Step 3: Implement small management components.**

```ts
export const ADMIN_PHOTOS_QUERY_KEY = ['admin-photos'] as const;
export const adminPhotoDetailQueryKey = (id: number) => [...ADMIN_PHOTOS_QUERY_KEY, 'detail', id] as const;
export const adminPhotoContentUrl = (id: number) => `/api/v1/admin/photos/${id}/content`;
export const replaceAdminPhoto = (id: number, file: File, revision: string) => {
  const form = new FormData(); form.append('photo', file);
  return apiFetch<void>(adminPhotoContentUrl(id), {
    method: 'PUT', body: form, headers: { 'If-Match': `"${revision}"` },
  });
};
export const removeAdminPhoto = (id: number, revision: string) =>
  apiFetch<void>(adminPhotoContentUrl(id), { method: 'DELETE', headers: { 'If-Match': `"${revision}"` } });
```

Management mutations use snapshot variables `{id,revision,file}` and existing session-generation protection. On success invalidate admin detail/list/operations and owner CATCHES_QUERY_KEY; do not manually increment revision based on guessed persistence behavior. On409 show “照片或记录已被修改，请刷新后重新确认操作”; refresh read state only, reset prior confirmation, never retrigger mutation. On401 clear session queries and redirect;403 hides/removes private preview and renders “没有管理员权限”, not a general retry loop.

Use object URL for local replacement preview and revoke it in effect cleanup; files only in component memory, no localStorage. Delete confirmation states record remains and old photo cannot be restored; replacement confirmation states the old photo will be removed. Loading operation history is independent of image loading failure and has retry/empty states; administrator evidence is read-only. Reuse typography/layout patterns, but do not reuse upload-capable owner panel in admin preview. Add navigation “照片管理” only for ADMIN and adjust AdminRoute explanation to “此页面仅供管理员进行后台管理。”

- [ ] **Step 4: Run focused GREEN then full frontend test/build/lint.** Validate both images and confirmation UI by tests; no type-ignore or permissive optional revision added to bypass fixture updates.
- [ ] **Step 5: Commit Task 4.** Commit `feat: add administrator photo management interface`.

### Task 5: Cross-role acceptance, live documentation and delivery gates

**Files:**
- Create: `e2e/tests/admin-photo-flow.spec.ts`
- Modify: existing owner photo e2e fixture/calls if they issue versionless requests.
- Create: `docs/runbooks/admin-photo-management.md`
- Modify: `docs/runbooks/local-development.md`
- Modify: `docs/runbooks/oss-private-media.md`
- Modify: `docs/superpowers/specs/2026-09-12-oss-private-media-design.md` (historical authorization explicitly superseded only by approved administrator management spec, not replaced as cloud-deployed evidence).
- Modify: `README.md` (runbook link).

**Interfaces:**
- Consumes: all Task 1–4 APIs/pages/protocols and exact counts/evidence, not just worker success reports.
- Produces: tested complete local feature, deployment caution and administrator usage instructions; no cloud changes or automatic integration.

- [ ] **Step 1: Write browser acceptance flow before final adjustments.** Separate browser contexts for owner, ADMIN and stranger. Owner creates a catch and uploads a valid small photo; admin filters by visible owner ID, opens preview, chooses replacement, cancels once (no change), confirms replacement; owner refresh sees replacement. Admin deletes with confirmation; owner sees no photo while catch fields remain. Stranger admin URL/API denied; anonymous photo read denied. Query database in trusted local test fixture to confirm operations correspond to the ADMIN, not to owner. Use a local isolated stack/project with disposable test-only data, never existing MinIO/RDS data.

```ts
await admin.getByRole('link', { name: '照片管理' }).click();
await admin.getByLabel('所属用户 ID').fill(String(ownerId));
await admin.getByRole('button', { name: '筛选' }).click();
await admin.getByRole('link', { name: `管理照片 ${recordId}` }).click();
await admin.getByLabel('新照片').setInputFiles({ name: 'new.png', mimeType: 'image/png', buffer: pngBytes });
await admin.getByRole('button', { name: '替换照片' }).click();
await admin.getByRole('button', { name: '取消' }).click();
await expect(admin.getByRole('alertdialog')).toHaveCount(0);
```

Use existing local administrator bootstrap values only in disposable local acceptance. Any setup/capacity failure is CannotVerify, not PASS. Add explicit conflict case with owner and admin tabs open at same revision: winner changes first, loser receives409, no auto retry, winner persists. Browser GET/no-store and account-switch content removal must be observed.

- [ ] **Step 2: Run acceptance and record behavior.** Backend `./mvnw -B test`, `./mvnw -B -DskipTests package`; frontend `npm test`, `npm run build`, `npm run lint`; e2e `cd e2e && npm test -- tests/admin-photo-flow.spec.ts` using isolated baseURL/config override if necessary. Confirm actual e2e/package scripts before running; use `npx playwright test tests/admin-photo-flow.spec.ts` if the package exposes no matching test script. Do not add libraries or relax Cookie security to start a cloud test.

- [ ] **Step 3: Update live instructions and superseded privacy assertions.** New runbook explains UI, role scope, If-Match quoting/statuses, version conflicts requiring renewed confirmation, no restore, exact operation schema, atomic cleanup, compensation-orphan residual risk, and local-only evidence. Replace active “only owner” assurances with owner/admin rule; label old OSS spec's owner-only read policy as historical and superseded by this management spec, retaining private Bucket/HTTPS requirements. Explain migration V10 is local-tested, not applied to RDS. Existing dependency-security-assessment's unverified Jackson patch claim remains a separately disclosed documentation finding, not silently treated as resolved by this feature.

- [ ] **Step 4: Review all changes and deliver.** `git diff --check`; explicit scope review prohibits public ACL/URL, cloud secrets, user-file edits, weakened tests, generic admin record write, new dependencies, and preemptive merge/push. Independent review should particularly inspect whole-record version mapping, native delete conditional behavior, transaction rollback and API role bypass. Collect all deviations/rulings with their risk; important findings must not be hidden by passing counts.
- [ ] **Step 5: Commit verified delivery documents/tests and hand off.** Commit `docs: record administrator photo validation and rollout boundaries`. Follow finishing-a-development-branch only after fresh full verification; user chooses local merge, push/PR or keep branch. No automatic merge implied by approval to implement this feature.

## Plan self-review checklist

- [x] Spec1–3 authorization and minimal exposure mapped to Tasks2/4/5.
- [x] Spec4 whole-record versions, owner clients and versioned native delete mapped to Tasks1/2/3.
- [x] Spec5 every route/error/cache constraint mapped to Tasks2/3/4 tests.
- [x] Spec6 admin-only successful evidence and rollback mapped to Tasks1/2/4/5.
- [x] Spec7 confirmation, session cleanup, preview cleanup and privacy copy mapped to Tasks3/4.
- [x] Spec8 full suites, isolated browser acceptance and no cloud scope mapped to Task5.
- [x] All interfaces use `version: Long` internally and `revision: string` over HTTP; controller uses `PhotoRevision.parse`, never optional permissive preconditions.
- [x] Every production step has named files and behavioral RED/GREEN coverage; no implementation placeholders or unknown helper types.
