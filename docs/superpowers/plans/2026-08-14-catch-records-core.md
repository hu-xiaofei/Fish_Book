# Catch Records Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver private, photo-optional-by-design catch-record CRUD with stable validation, ownership isolation, responsive React pages, and real-browser coverage.

**Architecture:** Add a `catchlog` vertical module whose domain stores a nullable `photoObjectKey` but whose first slice only accepts JSON record fields. Application services resolve the authenticated user through Identity, resolve fish through Catalog’s public query service, and enforce ownership by repository queries that always include both user ID and record ID. React keeps list/detail state in TanStack Query and form state in React Hook Form/Zod.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Security/Session/CSRF, Spring Data JPA, MySQL 8.4, Flyway, React 19, TypeScript 5.9, TanStack Query 5, React Hook Form 7, Zod 4, React Router 7, Vitest/Testing Library, Playwright.

## Global Constraints

- Complete `docs/superpowers/plans/2026-08-14-personal-favorites.md` first.
- Follow `docs/superpowers/specs/2026-08-14-personal-product-loop-design.md`.
- All catch endpoints require authentication; all writes require the existing CSRF token flow.
- Never accept `userId`; resolve it from the authenticated email through `ProfileApplicationService`.
- Every read, update, and delete repository operation includes both catch ID and current user ID; foreign records return the same 404 as missing records.
- The list is zero-based, fixed at 20 items, sorted by `caughtOn DESC, createdAt DESC, id DESC`, and rejects explicit `size`.
- `caughtOn` cannot be after today in `Asia/Shanghai`; inject a `Clock` into application tests.
- No photo endpoint or multipart request is introduced in this plan; `photo_object_key` remains nullable for Plan 3.
- Use TDD and make one focused commit per task after its named tests pass.

## File and Responsibility Map

**Backend**

- Create `backend/src/main/resources/db/migration/V7__create_catch_records.sql`.
- Create `backend/src/main/java/com/fishbook/catchlog/domain/*`: pure record, page, repository port, validation and not-found exceptions.
- Create `backend/src/main/java/com/fishbook/catchlog/persistence/*`: JPA entity/repository/adapter.
- Create `backend/src/main/java/com/fishbook/catchlog/application/*`: create/list/detail/update/delete use cases, command and views; summary/detail views expose `hasPhoto` but never the object key.
- Create `backend/src/main/java/com/fishbook/catchlog/web/CatchLogController.java`, scoped advice, and DTOs.
- Modify `SecurityConfig` to authenticate `/api/v1/catches/**`.

**Frontend**

- Create `frontend/src/features/catchlog/model/{types,catchRecordSchema}.ts`.
- Create `frontend/src/features/catchlog/api/catchLogApi.ts`.
- Create `frontend/src/features/catchlog/components/{CatchRecordForm,CatchPagination}.tsx`.
- Create `frontend/src/features/catchlog/pages/{CatchListPage,CatchCreatePage,CatchDetailPage,CatchEditPage}.tsx` and focused CSS modules.
- Modify router and authenticated navigation.

**Tests and docs**

- Add migration, domain, persistence, application, Web/security, frontend API/form/page, and Playwright tests beside the implementation.
- Create `e2e/tests/catch-records-flow.spec.ts` and `e2e/tests/catch-record-authorization.spec.ts`.
- Update README only after the full browser flow passes.

---

### Task 1: Create Catch Schema and Pure Domain Model

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__create_catch_records.sql`
- Create: `backend/src/main/java/com/fishbook/catchlog/domain/CatchRecord.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/domain/CatchRecordPage.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/domain/CatchRecordRepository.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/domain/InvalidCatchRecordException.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/domain/CatchRecordNotFoundException.java`
- Create: `backend/src/test/java/com/fishbook/catchlog/persistence/CatchRecordDatabaseMigrationTest.java`
- Create: `backend/src/test/java/com/fishbook/catchlog/domain/CatchRecordTest.java`

**Interfaces:**
- Produces a persistence-neutral `CatchRecord` with nullable ID and photo key.
- Produces repository operations consumed by Task 2.

- [ ] **Step 1: Write the failing migration test**

Assert `catch_records` exists, nullable columns match the spec, `photo_object_key` is nullable, `ix_catch_records_user_caught_created_id` exists, and the user/fish foreign keys use `RESTRICT`. Insert a negative length and assert `ck_catch_records_length` rejects it.

- [ ] **Step 2: Run the migration test and verify RED**

Run: `cd backend && ./mvnw -Dtest=CatchRecordDatabaseMigrationTest test`

Expected: failure because `catch_records` does not exist.

- [ ] **Step 3: Create V7 exactly**

```sql
CREATE TABLE catch_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    fish_species_id BIGINT NOT NULL,
    caught_on DATE NOT NULL,
    location VARCHAR(200) NOT NULL,
    length_cm DECIMAL(8,2) NULL,
    weight_g DECIMAL(10,2) NULL,
    method VARCHAR(100) NULL,
    notes TEXT NULL,
    photo_object_key VARCHAR(512) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_catch_records PRIMARY KEY (id),
    CONSTRAINT fk_catch_records_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_catch_records_fish FOREIGN KEY (fish_species_id)
        REFERENCES fish_species (id) ON DELETE RESTRICT,
    CONSTRAINT ck_catch_records_length CHECK (length_cm IS NULL OR length_cm >= 0),
    CONSTRAINT ck_catch_records_weight CHECK (weight_g IS NULL OR weight_g >= 0),
    INDEX ix_catch_records_user_caught_created_id
        (user_id, caught_on DESC, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- [ ] **Step 4: Write failing domain boundary tests**

Test blank/over-200 location, negative length/weight, over-100 method, over-5,000 notes, trimming optional blank fields to null, and preservation of nullable `photoObjectKey`. Use exact record shape:

```java
public record CatchRecord(
        Long id, long userId, long fishId, LocalDate caughtOn, String location,
        BigDecimal lengthCm, BigDecimal weightG, String method, String notes,
        String photoObjectKey, Instant createdAt, Instant updatedAt) { ... }
```

- [ ] **Step 5: Implement minimal domain and repository port**

Expose factories `create(...)` and `reconstitute(...)`; structural validation belongs in the domain, while “future date” belongs in the clock-aware application service. Define:

```java
public interface CatchRecordRepository {
    CatchRecord save(CatchRecord record);
    Optional<CatchRecord> findOwnedById(long recordId, long userId);
    CatchRecordPage findByUserId(long userId, int page, int size);
    void delete(CatchRecord record);
}
```

`InvalidCatchRecordException.code()` returns `INVALID_CATCH_RECORD`; `CatchRecordNotFoundException.code()` returns `CATCH_RECORD_NOT_FOUND`.

- [ ] **Step 6: Verify schema and domain GREEN**

Run: `cd backend && ./mvnw -Dtest=CatchRecordDatabaseMigrationTest,CatchRecordTest test`

Expected: all selected tests pass.

- [ ] **Step 7: Commit schema and domain**

```bash
git add backend/src/main/resources/db/migration/V7__create_catch_records.sql backend/src/main/java/com/fishbook/catchlog/domain backend/src/test/java/com/fishbook/catchlog
git commit -m "feat: define private catch records"
```

### Task 2: Implement the Owned Catch Repository Adapter

**Files:**
- Create: `backend/src/main/java/com/fishbook/catchlog/persistence/CatchRecordJpaEntity.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/persistence/SpringDataCatchRecordJpaRepository.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/persistence/JpaCatchRecordRepositoryAdapter.java`
- Create: `backend/src/test/java/com/fishbook/catchlog/persistence/JpaCatchRecordRepositoryAdapterTest.java`

**Interfaces:**
- Consumes: Task 1 domain and repository port.
- Produces: ownership-limited persistence; no caller can fetch by ID without user ID through the port.

- [ ] **Step 1: Write failing real-MySQL repository tests**

Create two users and records with interleaved dates. Assert `findOwnedById(id, owner)` returns data, `findOwnedById(id, otherUser)` is empty, pages sort by the required three fields, save preserves `photoObjectKey`, and delete removes only the supplied owned entity.

- [ ] **Step 2: Run and verify RED**

Run: `cd backend && ./mvnw -Dtest=JpaCatchRecordRepositoryAdapterTest test`

Expected: compilation failure because persistence classes do not exist.

- [ ] **Step 3: Implement scalar JPA mapping and owned queries**

Map `user_id` and `fish_species_id` as scalar longs; do not import Identity or Catalog JPA entities. Define Spring Data methods:

```java
Optional<CatchRecordJpaEntity> findByIdAndUserId(long id, long userId);
Page<CatchRecordJpaEntity> findByUserId(long userId, Pageable pageable);
```

The adapter builds `PageRequest.of(page, size, Sort.by(Sort.Order.desc("caughtOn"), Sort.Order.desc("createdAt"), Sort.Order.desc("id")))`, maps all fields, and marks query methods read-only transactions.

- [ ] **Step 4: Verify GREEN**

Run: `cd backend && ./mvnw -Dtest=JpaCatchRecordRepositoryAdapterTest test`

Expected: all repository tests pass against MySQL 8.4.

- [ ] **Step 5: Commit persistence**

```bash
git add backend/src/main/java/com/fishbook/catchlog/persistence backend/src/test/java/com/fishbook/catchlog/persistence/JpaCatchRecordRepositoryAdapterTest.java
git commit -m "feat: persist owned catch records"
```

### Task 3: Implement Clock-Aware Catch Application Use Cases

**Files:**
- Create: `backend/src/main/java/com/fishbook/catchlog/application/SaveCatchRecordCommand.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchRecordApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/DefaultCatchRecordApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchFishView.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchRecordSummaryView.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchRecordDetailView.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchRecordPageView.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/InvalidCatchQueryException.java`
- Create: `backend/src/test/java/com/fishbook/catchlog/application/DefaultCatchRecordApplicationServiceTest.java`

**Interfaces:**
- Consumes: `ProfileApplicationService`, Catalog reference/summary methods from Plan 1, and `CatchRecordRepository`.
- Produces: email-based CRUD use cases and views with embedded fish display data.

- [ ] **Step 1: Write failing application tests with a fixed Shanghai clock**

Use `Clock.fixed(Instant.parse("2026-08-14T04:00:00Z"), ZoneId.of("Asia/Shanghai"))`. Assert Aug 14 succeeds and Aug 15 fails; create resolves user/fish IDs; list preserves repository order; detail/update/delete use the owned query; update preserves ID, owner, `createdAt`, and `photoObjectKey`; update changes `updatedAt`; page `-1` and explicit size raise `INVALID_CATCH_QUERY`.

- [ ] **Step 2: Run and verify RED**

Run: `cd backend && ./mvnw -Dtest=DefaultCatchRecordApplicationServiceTest test`

Expected: compilation failure because use-case types do not exist.

- [ ] **Step 3: Implement exact application API**

```java
public interface CatchRecordApplicationService {
    CatchRecordDetailView create(String authenticatedEmail, SaveCatchRecordCommand command);
    CatchRecordPageView list(String authenticatedEmail, int page);
    CatchRecordDetailView get(String authenticatedEmail, long recordId);
    CatchRecordDetailView update(
            String authenticatedEmail, long recordId, SaveCatchRecordCommand command);
    void delete(String authenticatedEmail, long recordId);
}
```

`SaveCatchRecordCommand` contains `fishSlug`, `caughtOn`, `location`, `lengthCm`, `weightG`, `method`, and `notes`. Use `PAGE_SIZE = 20`. Both `CatchRecordSummaryView` and `CatchRecordDetailView` expose `boolean hasPhoto`, derived from the nullable domain key, and never expose that key. The Spring constructor uses `Clock.system(ZoneId.of("Asia/Shanghai"))`; keep a package-visible constructor accepting `Clock` for tests. Resolve summaries with one Catalog batch call per page, never one call per row.

- [ ] **Step 4: Verify GREEN**

Run: `cd backend && ./mvnw -Dtest=DefaultCatchRecordApplicationServiceTest test`

Expected: all use-case tests pass.

- [ ] **Step 5: Commit use cases**

```bash
git add backend/src/main/java/com/fishbook/catchlog/application backend/src/test/java/com/fishbook/catchlog/application
git commit -m "feat: add catch record use cases"
```

### Task 4: Expose Secure Catch CRUD API and Stable Errors

**Files:**
- Create: `backend/src/main/java/com/fishbook/catchlog/web/CatchLogController.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/web/CatchLogExceptionHandler.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/web/dto/SaveCatchRecordRequest.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/web/dto/CatchFishResponse.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/web/dto/CatchRecordSummaryResponse.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/web/dto/CatchRecordDetailResponse.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/web/dto/CatchRecordPageResponse.java`
- Modify: `backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java`
- Create: `backend/src/test/java/com/fishbook/catchlog/web/CatchRecordApiIntegrationTest.java`
- Create: `backend/src/test/java/com/fishbook/catchlog/web/CatchRecordAuthorizationTest.java`

**Interfaces:**
- Produces: POST/GET/PUT/DELETE `/api/v1/catches` endpoints from the design.
- Produces: `INVALID_CATCH_RECORD`, `INVALID_CATCH_QUERY`, and `CATCH_RECORD_NOT_FOUND` safe errors.

- [ ] **Step 1: Write failing API contract tests**

Cover POST 201 with `Location`, list size 20 metadata, detail, full PUT, DELETE 204, future date, blank/long location, negative values, missing fish, page `-1`, explicit size, malformed JSON, missing record, and another user’s record returning the same `CATCH_RECORD_NOT_FOUND` body. Assert responses never include `userId` or `photoObjectKey`.

- [ ] **Step 2: Write failing authorization tests**

Assert all anonymous methods return 401, writes without CSRF return `CSRF_INVALID`, and valid authenticated CSRF requests reach the controller.

- [ ] **Step 3: Run and verify RED**

Run: `cd backend && ./mvnw -Dtest=CatchRecordApiIntegrationTest,CatchRecordAuthorizationTest test`

Expected: endpoint-not-found/deny-all failures.

- [ ] **Step 4: Implement validated DTO and scoped advice**

`SaveCatchRecordRequest` uses `@NotBlank`, `@Size`, `@NotNull`, and `@DecimalMin("0")`, then maps to `SaveCatchRecordCommand`. Create `@RestControllerAdvice(assignableTypes = CatchLogController.class)` with highest precedence so DTO validation and unreadable bodies map to `INVALID_CATCH_RECORD`; map `InvalidCatchQueryException` to 400 and `CatchRecordNotFoundException` to 404. Do not return exception messages containing field values.

Add `/api/v1/catches/**` to `authenticationRequiredEndpoints`. Controller signatures use `Authentication.getName()`, parse absent page as zero, reject explicit size, and never bind `userId`.

- [ ] **Step 5: Verify focused plus auth/catalog regression**

Run:

`cd backend && ./mvnw -Dtest=CatchRecordApiIntegrationTest,CatchRecordAuthorizationTest,AuthFlowIntegrationTest,CatalogAuthorizationTest,FavoriteAuthorizationTest test`

Expected: all selected tests pass.

- [ ] **Step 6: Commit the HTTP slice**

```bash
git add backend/src/main/java/com/fishbook/catchlog/web backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java backend/src/test/java/com/fishbook/catchlog/web
git commit -m "feat: expose private catch records api"
```

### Task 5: Add Typed Catch API and Reusable Form

**Files:**
- Create: `frontend/src/features/catchlog/model/types.ts`
- Create: `frontend/src/features/catchlog/model/catchRecordSchema.ts`
- Create: `frontend/src/features/catchlog/model/catchRecordSchema.test.ts`
- Create: `frontend/src/features/catchlog/api/catchLogApi.ts`
- Create: `frontend/src/features/catchlog/api/catchLogApi.test.ts`
- Create: `frontend/src/features/catchlog/components/CatchRecordForm.tsx`
- Create: `frontend/src/features/catchlog/components/CatchRecordForm.test.tsx`
- Create: `frontend/src/features/catchlog/components/CatchRecordForm.module.css`

**Interfaces:**
- Produces: `CatchRecordInput`, summary/detail/page types, query keys, CRUD API functions, and a form used by create/edit pages.

- [ ] **Step 1: Write failing schema tests**

With a supplied `today = '2026-08-14'`, assert required fish/date/location, future-date rejection, trimming, empty optional numeric fields becoming null, nonnegative decimals, 100-character method, and 5,000-character notes.

- [ ] **Step 2: Write failing API tests**

Assert zero-based query strings, encoded IDs, exact POST/PUT JSON, DELETE method, and query keys:

```ts
expect(catchListQueryKey(2)).toEqual(['catch-records', 'list', 2]);
expect(catchDetailQueryKey(42)).toEqual(['catch-records', 'detail', 42]);
```

- [ ] **Step 3: Run and verify RED**

Run: `cd frontend && npm test -- src/features/catchlog/model/catchRecordSchema.test.ts src/features/catchlog/api/catchLogApi.test.ts`

Expected: module-not-found failures.

- [ ] **Step 4: Implement exact types and functions**

```ts
export type CatchRecordInput = {
  fishSlug: string;
  caughtOn: string;
  location: string;
  lengthCm: number | null;
  weightG: number | null;
  method: string | null;
  notes: string | null;
};
```

Implement `fetchCatchPage`, `fetchCatchDetail`, `createCatchRecord`, `updateCatchRecord`, and `deleteCatchRecord` through `apiFetch` with root key `['catch-records']`.

- [ ] **Step 5: Write and implement the reusable form test-first**

Test labeled fish/date/location/length/weight/method/notes fields, server-safe generic error region, disabled submit state, and invocation with normalized `CatchRecordInput`. Load current fish choices with the existing catalog page query using empty filters; current scope contains exactly 12 fish. Do not include file input or photo controls.

Run: `cd frontend && npm test -- src/features/catchlog && npm run lint`

Expected: all catch model/API/form tests and lint pass.

- [ ] **Step 6: Commit typed client and form**

```bash
git add frontend/src/features/catchlog/model frontend/src/features/catchlog/api frontend/src/features/catchlog/components
git commit -m "feat: add catch record form client"
```

### Task 6: Build Create, List, and Detail Pages

**Files:**
- Create: `frontend/src/features/catchlog/pages/CatchCreatePage.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchCreatePage.test.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchListPage.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchListPage.test.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchDetailPage.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchDetailPage.test.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchLogPages.module.css`
- Create: `frontend/src/features/catchlog/components/CatchPagination.tsx`
- Modify: `frontend/src/app/router.tsx`

**Interfaces:**
- Produces protected `/catches`, `/catches/new`, and `/catches/:id` routes.

- [ ] **Step 1: Write failing page tests**

Create page: submit normalized input, invalidate `['catch-records']`, then navigate to `/catches/{id}`. List page: loading, empty, populated, retry, fixed pagination and links. Detail page: loading, safe error, 404 state, all fields, “暂无” for null optional values, and edit/delete navigation placeholders.

- [ ] **Step 2: Run and verify RED**

Run: `cd frontend && npm test -- src/features/catchlog/pages`

Expected: missing page/route failures.

- [ ] **Step 3: Implement the three pages and protected routes**

Use TanStack Query for remote state and existing `ProtectedRoute`. The list shows fish name, caught date, location, optional length/weight summary, and “暂无照片” without requesting media. The detail page never renders internal IDs except its own route ID and never exposes `photoObjectKey`.

- [ ] **Step 4: Verify page tests and build**

Run: `cd frontend && npm test -- src/features/catchlog/pages && npm run lint && npm run build`

Expected: selected tests, lint, and build pass.

- [ ] **Step 5: Commit usable create/read flow**

```bash
git add frontend/src/features/catchlog frontend/src/app/router.tsx
git commit -m "feat: add catch record pages"
```

### Task 7: Add Edit, Delete, Navigation, Browser Isolation, and Docs

**Files:**
- Create: `frontend/src/features/catchlog/pages/CatchEditPage.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchEditPage.test.tsx`
- Modify: `frontend/src/features/catchlog/pages/CatchDetailPage.tsx`
- Modify: `frontend/src/features/catchlog/pages/CatchDetailPage.test.tsx`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/features/auth/components/SessionNav.tsx`
- Modify: `frontend/src/features/auth/components/SessionNav.test.tsx`
- Create: `e2e/tests/catch-records-flow.spec.ts`
- Create: `e2e/tests/catch-record-authorization.spec.ts`
- Modify: `README.md`

**Interfaces:**
- Produces protected `/catches/:id/edit`, full CRUD acceptance, and user-isolation evidence.

- [ ] **Step 1: Write failing edit and delete UI tests**

Assert edit loads existing values, PUTs normalized input, invalidates list/detail and returns to detail. Assert delete requires confirmation, DELETEs, removes cached detail, invalidates list, and navigates to `/catches`. A canceled confirmation must not call the API. Assert authenticated navigation now includes “钓获记录” pointing to the route created in this plan.

- [ ] **Step 2: Implement edit/delete and verify frontend GREEN**

Run: `cd frontend && npm test && npm run lint && npm run build`

Expected: all frontend checks pass.

- [ ] **Step 3: Write the two failing Playwright scenarios**

Flow test: register/login, create an 乌鳢 record without a photo, assert detail, edit location/weight, reload, delete, and assert empty list. Authorization test: create a record as user A; use a second isolated browser context for user B; assert direct detail navigation shows not found and API GET returns 404. Fetch `/api/v1/auth/csrf` as user B, read the `XSRF-TOKEN` cookie, send it as `X-XSRF-TOKEN` on API PUT/DELETE, and assert both ownership-checked writes return 404 rather than a CSRF-only 403.

- [ ] **Step 4: Start stack, run scenarios, and close exact gaps**

Run:

```bash
docker compose -f compose.yaml -f compose.full.yaml up -d --build
cd e2e && npm test -- catch-records-flow.spec.ts catch-record-authorization.spec.ts
```

Expected after Tasks 1–7 UI work: both scenarios pass. If either fails, preserve the exact failing assertion, invoke `superpowers:systematic-debugging`, and add a focused regression test before changing production code.

- [ ] **Step 5: Update README without claiming photos**

Document private catch CRUD and user isolation. Keep photos listed as the next milestone and retain the MinIO statement as provisioned infrastructure, not active storage.

- [ ] **Step 6: Run the Plan 2 completion gate**

```bash
cd backend && ./mvnw test
cd ../frontend && npm run lint && npm test && npm run build
cd ../e2e && npm test
cd .. && docker compose --env-file .env.example -f compose.yaml -f compose.full.yaml config --quiet
```

Expected: every command exits 0.

- [ ] **Step 7: Commit milestone two**

```bash
git add frontend/src/features/catchlog frontend/src/app/router.tsx frontend/src/features/auth/components e2e/tests README.md
git commit -m "test: verify private catch record flow"
```

## Plan 2 Completion Gate

- All Plan 2 completion commands exit 0 and `git status --short` is empty.
- A user can complete CRUD without a photo.
- A second user receives the same 404 for a foreign record as for a missing record.
- `catch_records.photo_object_key` remains nullable and no multipart endpoint exists yet.
- Continue with `docs/superpowers/plans/2026-08-14-catch-photo-media.md` only after this gate passes.
