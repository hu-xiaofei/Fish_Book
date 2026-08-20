# Catch Records Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver milestone two of the personal-product loop so an authenticated user can create, list, view, fully update, and delete private catch records without uploading a photo.

**Architecture:** Add a `catchlog` vertical slice to the Spring Boot modular monolith, following the existing Controller → Application Service → Domain / Repository Port → JPA Adapter dependency direction. The React `catchlog` feature uses the shared credentialed API client, TanStack Query, protected routes, React Hook Form, and Zod; all ownership decisions stay in backend queries scoped by both record ID and current user ID.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, MySQL 8.4, Flyway, React 19, TypeScript 5.9, React Router, TanStack Query, React Hook Form, Zod, JUnit, Testcontainers, Vitest, Testing Library, Playwright

**Spec:** `docs/superpowers/specs/2026-08-14-personal-product-loop-design.md`

## Global Constraints

- This plan implements only milestone two: no photo upload, media module, cleanup job, statistics, search, map, weather, sharing, community, or administration behavior.
- `catch_records` includes nullable `photo_object_key` now for forward compatibility, but this milestone never accepts or returns an object key.
- `caught_on` must not be later than the current date in `Asia/Shanghai`; backend validation uses an injected `Clock`.
- `location` is stripped and must contain 1–200 characters.
- `method` is stripped, stored as `NULL` when blank, and limited to 100 characters.
- `notes` is stripped, stored as `NULL` when blank, and limited to 5,000 characters.
- `length_cm` and `weight_g` are optional and non-negative; application validation also enforces the `DECIMAL(8,2)` and `DECIMAL(10,2)` storage bounds.
- Catch list pages contain exactly 20 records and reject a client-supplied `size` parameter.
- List order is `caughtOn DESC, createdAt DESC, id DESC`.
- Every read, update, and delete repository operation is scoped by both the current user ID and record ID; a missing record and another user's record both produce `404 CATCH_RECORD_NOT_FOUND`.
- POST returns `201 Created`, a `Location: /api/v1/catches/{id}` header, and the complete record. DELETE returns `204 No Content`; repeated delete returns `404 CATCH_RECORD_NOT_FOUND`.
- All catch routes require the existing session cookie. Unsafe requests require the existing CSRF token.
- Use strict red-green-refactor cycles: add one failing test, run it and confirm the expected failure, add only enough production code to pass, then rerun the focused suite before continuing.

## Planned File Structure

```text
backend/src/main/java/com/fishbook/catchlog/
├── application/
│   ├── CatchRecordApplicationService.java
│   ├── CatchRecordCommand.java
│   ├── CatchRecordDetailView.java
│   ├── CatchRecordPageView.java
│   ├── CatchRecordSummaryView.java
│   ├── CatchLogClockConfiguration.java
│   ├── DefaultCatchRecordApplicationService.java
│   └── InvalidCatchRecordQueryException.java
├── domain/
│   ├── CatchRecord.java
│   ├── CatchRecordDetails.java
│   ├── CatchRecordNotFoundException.java
│   ├── CatchRecordPage.java
│   ├── CatchRecordRepository.java
│   └── InvalidCatchRecordException.java
├── persistence/
│   ├── CatchRecordJpaEntity.java
│   ├── JpaCatchRecordRepositoryAdapter.java
│   └── SpringDataCatchRecordJpaRepository.java
└── web/
    ├── CatchRecordController.java
    └── dto/
        ├── CatchRecordDetailResponse.java
        ├── CatchRecordPageResponse.java
        ├── CatchRecordRequest.java
        └── CatchRecordSummaryResponse.java

frontend/src/features/catchlog/
├── api/catchRecordsApi.ts
├── api/catchRecordsApi.test.ts
├── components/CatchRecordForm.tsx
├── components/CatchRecordForm.test.tsx
├── model/catchRecordForm.ts
├── model/catchRecordForm.test.ts
├── model/types.ts
├── pages/CatchDetailPage.tsx
├── pages/CatchDetailPage.test.tsx
├── pages/CatchEditPage.tsx
├── pages/CatchEditPage.test.tsx
├── pages/CatchListPage.tsx
├── pages/CatchListPage.test.tsx
├── pages/CatchNewPage.tsx
├── pages/CatchNewPage.test.tsx
└── pages/CatchPages.module.css
```

---

### Task 1: Define and Validate the Catch Record Domain

**Files:**
- Create: `backend/src/main/java/com/fishbook/catchlog/domain/InvalidCatchRecordException.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/domain/CatchRecordNotFoundException.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/domain/CatchRecordDetails.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/domain/CatchRecord.java`
- Test: `backend/src/test/java/com/fishbook/catchlog/domain/CatchRecordTest.java`

**Interfaces:**
- Consumes: `LocalDate today` calculated by the application service from the injected business clock.
- Produces: `CatchRecordDetails.validated(...)`, `CatchRecord.create(...)`, `CatchRecord.restore(...)`, and `CatchRecord.update(...)` for persistence and application tasks.

- [ ] **Step 1: Write the failing validation tests**

Add focused tests that specify normalization and every storage/input boundary:

```java
@Test
void stripsRequiredAndOptionalTextAndTurnsBlankOptionalTextIntoNull() {
    CatchRecordDetails details = CatchRecordDetails.validated(
            7L, LocalDate.parse("2026-08-20"), "  城郊水库  ",
            new BigDecimal("42.50"), new BigDecimal("1350.00"),
            "   ", "  傍晚近岸中鱼  ", LocalDate.parse("2026-08-20"));

    assertThat(details.location()).isEqualTo("城郊水库");
    assertThat(details.method()).isNull();
    assertThat(details.notes()).isEqualTo("傍晚近岸中鱼");
}

@Test
void rejectsFutureDatesNegativeMeasurementsAndColumnOverflow() {
    LocalDate today = LocalDate.parse("2026-08-20");
    assertThatThrownBy(() -> validDetails(today.plusDays(1), null, null, today))
            .isInstanceOf(InvalidCatchRecordException.class);
    assertThatThrownBy(() -> validDetails(today, new BigDecimal("-0.01"), null, today))
            .isInstanceOf(InvalidCatchRecordException.class);
    assertThatThrownBy(() -> validDetails(today, new BigDecimal("1000000.00"), null, today))
            .isInstanceOf(InvalidCatchRecordException.class);
    assertThatThrownBy(() -> validDetails(today, null, new BigDecimal("100000000.00"), today))
            .isInstanceOf(InvalidCatchRecordException.class);
}
```

Also test null date, blank/201-character location, 101-character method, 5,001-character notes, zero measurements, create timestamps, update preserving `id`, `userId`, `createdAt`, and `photoObjectKey`, plus stable exception codes `INVALID_CATCH_RECORD` and `CATCH_RECORD_NOT_FOUND`.

- [ ] **Step 2: Run the domain test and verify RED**

Run: `cd backend && ./mvnw -Dtest=CatchRecordTest test`

Expected: compilation failure because the `catchlog.domain` types do not exist.

- [ ] **Step 3: Implement the minimal domain model**

Use this public shape:

```java
public record CatchRecordDetails(
        long fishId,
        LocalDate caughtOn,
        String location,
        BigDecimal lengthCm,
        BigDecimal weightG,
        String method,
        String notes) {

    public static CatchRecordDetails validated(
            long fishId, LocalDate caughtOn, String location,
            BigDecimal lengthCm, BigDecimal weightG,
            String method, String notes, LocalDate today) {
        Objects.requireNonNull(today, "today must not be null");
        if (fishId <= 0 || caughtOn == null || caughtOn.isAfter(today)) {
            throw new InvalidCatchRecordException("fish and caught date must be valid");
        }
        String normalizedLocation = requiredText(location, 200, "location");
        String normalizedMethod = optionalText(method, 100, "method");
        String normalizedNotes = optionalText(notes, 5_000, "notes");
        BigDecimal normalizedLength = measurement(
                lengthCm, new BigDecimal("999999.99"), "lengthCm");
        BigDecimal normalizedWeight = measurement(
                weightG, new BigDecimal("99999999.99"), "weightG");
        return new CatchRecordDetails(
                fishId, caughtOn, normalizedLocation, normalizedLength,
                normalizedWeight, normalizedMethod, normalizedNotes);
    }

    private static String requiredText(String value, int max, String field) {
        if (value == null || value.strip().isEmpty() || value.strip().length() > max) {
            throw new InvalidCatchRecordException(field + " is invalid");
        }
        return value.strip();
    }

    private static String optionalText(String value, int max, String field) {
        if (value == null || value.strip().isEmpty()) return null;
        if (value.strip().length() > max) {
            throw new InvalidCatchRecordException(field + " is invalid");
        }
        return value.strip();
    }

    private static BigDecimal measurement(BigDecimal value, BigDecimal max, String field) {
        if (value == null) return null;
        if (value.signum() < 0 || value.compareTo(max) > 0
                || value.stripTrailingZeros().scale() > 2) {
            throw new InvalidCatchRecordException(field + " is invalid");
        }
        return value;
    }
}

public record CatchRecord(
        Long id,
        long userId,
        CatchRecordDetails details,
        String photoObjectKey,
        Instant createdAt,
        Instant updatedAt) {

    public CatchRecord {
        Objects.requireNonNull(details, "details must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (userId <= 0 || (id != null && id <= 0)) {
            throw new IllegalArgumentException("record and user IDs must be positive");
        }
    }

    public static CatchRecord create(long userId, CatchRecordDetails details, Instant now) {
        return new CatchRecord(null, userId, details, null, now, now);
    }

    public static CatchRecord restore(
            long id, long userId, CatchRecordDetails details, String photoObjectKey,
            Instant createdAt, Instant updatedAt) {
        return new CatchRecord(id, userId, details, photoObjectKey, createdAt, updatedAt);
    }

    public CatchRecord update(CatchRecordDetails next, Instant now) {
        return new CatchRecord(id, userId, next, photoObjectKey, createdAt, now);
    }
}
```

`restore(...)` must accept persisted values without re-evaluating a historical `caughtOn` against today's date, while still requiring non-null IDs and timestamps.

- [ ] **Step 4: Run the domain test and verify GREEN**

Run: `cd backend && ./mvnw -Dtest=CatchRecordTest test`

Expected: `CatchRecordTest` passes with no failures.

- [ ] **Step 5: Commit the domain slice**

```bash
git add backend/src/main/java/com/fishbook/catchlog/domain backend/src/test/java/com/fishbook/catchlog/domain
git commit -m "feat: define catch record domain rules"
```

---

### Task 2: Persist Private Catch Records with Stable Pagination

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__create_catch_records.sql`
- Create: `backend/src/main/java/com/fishbook/catchlog/domain/CatchRecordPage.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/domain/CatchRecordRepository.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/persistence/CatchRecordJpaEntity.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/persistence/SpringDataCatchRecordJpaRepository.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/persistence/JpaCatchRecordRepositoryAdapter.java`
- Test: `backend/src/test/java/com/fishbook/catchlog/persistence/CatchRecordDatabaseMigrationTest.java`
- Test: `backend/src/test/java/com/fishbook/catchlog/persistence/JpaCatchRecordRepositoryAdapterTest.java`

**Interfaces:**
- Consumes: `CatchRecord` and `CatchRecordDetails` from Task 1.
- Produces: `CatchRecordRepository.save`, `findByIdAndUserId`, `findByUserId`, and `deleteByIdAndUserId` for Task 3.

- [ ] **Step 1: Write the failing migration test**

Verify the table, keys, index, nullable photo column, and restricted foreign keys:

```java
@Test
void createsCatchRecordsWithOwnershipForeignKeysAndStableSortIndex() {
    assertThat(jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
            String.class)).contains("catch_records");
    assertThat(jdbcTemplate.queryForList(
            "SELECT constraint_name FROM information_schema.table_constraints "
                    + "WHERE table_schema = DATABASE() AND table_name = 'catch_records'",
            String.class)).contains("pk_catch_records", "fk_catch_records_user", "fk_catch_records_fish");
    assertThat(jdbcTemplate.queryForList(
            "SELECT DISTINCT index_name FROM information_schema.statistics "
                    + "WHERE table_schema = DATABASE() AND table_name = 'catch_records'",
            String.class)).contains("ix_catch_records_user_caught_created_id");
}
```

- [ ] **Step 2: Run the migration test and verify RED**

Run: `cd backend && ./mvnw -Dtest=CatchRecordDatabaseMigrationTest test`

Expected: FAIL because `catch_records` does not exist.

- [ ] **Step 3: Add the Flyway migration**

Create the table exactly as follows:

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
    INDEX ix_catch_records_user_caught_created_id
        (user_id, caught_on DESC, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- [ ] **Step 4: Run the migration test and verify GREEN**

Run: `cd backend && ./mvnw -Dtest=CatchRecordDatabaseMigrationTest test`

Expected: PASS on an empty Testcontainers MySQL database.

- [ ] **Step 5: Write failing repository adapter tests**

Cover create/read/update/delete, page metadata, stable tie-breaking, and user isolation. The ownership assertion must call the public adapter API, not inspect only SQL:

```java
@Test
void scopesDetailUpdateAndDeleteByBothRecordAndUser() {
    CatchRecord saved = adapter.save(recordFor(USER_ID, 1L, "我的水库"));

    assertThat(adapter.findByIdAndUserId(saved.id(), OTHER_USER_ID)).isEmpty();
    assertThat(adapter.deleteByIdAndUserId(saved.id(), OTHER_USER_ID)).isFalse();
    assertThat(adapter.findByIdAndUserId(saved.id(), USER_ID)).contains(saved);
}

@Test
void pagesByCaughtOnCreatedAtAndIdDescendingWithoutOtherUsers() {
    CatchRecord lowerId = adapter.save(recordFor(
            USER_ID, 1L, LocalDate.parse("2026-08-20"),
            Instant.parse("2026-08-20T01:00:00Z"), "一号点"));
    CatchRecord higherId = adapter.save(recordFor(
            USER_ID, 2L, LocalDate.parse("2026-08-20"),
            Instant.parse("2026-08-20T01:00:00Z"), "二号点"));
    adapter.save(recordFor(
            USER_ID, 3L, LocalDate.parse("2026-08-19"),
            Instant.parse("2026-08-20T02:00:00Z"), "三号点"));
    adapter.save(recordFor(
            OTHER_USER_ID, 4L, LocalDate.parse("2026-08-21"),
            Instant.parse("2026-08-20T03:00:00Z"), "其他用户"));

    CatchRecordPage page = adapter.findByUserId(USER_ID, 0, 2);
    assertThat(page.items()).extracting(CatchRecord::id)
            .containsExactly(higherId.id(), lowerId.id());
    assertThat(page.totalItems()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(2);
}
```

- [ ] **Step 6: Run the repository tests and verify RED**

Run: `cd backend && ./mvnw -Dtest=JpaCatchRecordRepositoryAdapterTest test`

Expected: compilation failure because the repository port and adapter do not exist.

- [ ] **Step 7: Implement the repository port and JPA adapter**

Use these signatures:

```java
public interface CatchRecordRepository {
    CatchRecord save(CatchRecord record);
    Optional<CatchRecord> findByIdAndUserId(long id, long userId);
    CatchRecordPage findByUserId(long userId, int page, int size);
    boolean deleteByIdAndUserId(long id, long userId);
}

interface SpringDataCatchRecordJpaRepository
        extends JpaRepository<CatchRecordJpaEntity, Long> {
    Optional<CatchRecordJpaEntity> findByIdAndUserId(long id, long userId);
    Page<CatchRecordJpaEntity> findByUserId(long userId, Pageable pageable);
    long deleteByIdAndUserId(long id, long userId);
}
```

The adapter's `PageRequest` must use `caughtOn DESC`, `createdAt DESC`, and `id DESC`. Entity mapping must preserve the nullable `photoObjectKey` without exposing it in web DTOs.

- [ ] **Step 8: Run persistence tests and the existing migration suite**

Run: `cd backend && ./mvnw -Dtest=CatchRecordDatabaseMigrationTest,JpaCatchRecordRepositoryAdapterTest,DatabaseMigrationTest,CatalogDatabaseMigrationTest,FavoriteDatabaseMigrationTest test`

Expected: all selected tests pass.

- [ ] **Step 9: Commit the persistence slice**

```bash
git add backend/src/main/resources/db/migration/V7__create_catch_records.sql backend/src/main/java/com/fishbook/catchlog backend/src/test/java/com/fishbook/catchlog/persistence
git commit -m "feat: persist private catch records"
```

---

### Task 3: Implement Authenticated Catch Record Use Cases

**Files:**
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchRecordCommand.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchRecordApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/DefaultCatchRecordApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchRecordSummaryView.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchRecordDetailView.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchRecordPageView.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/InvalidCatchRecordQueryException.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/application/CatchLogClockConfiguration.java`
- Test: `backend/src/test/java/com/fishbook/catchlog/application/DefaultCatchRecordApplicationServiceTest.java`

**Interfaces:**
- Consumes: `ProfileApplicationService.currentUser`, `FishCatalogQueryService.getReferenceBySlug`, `getSummariesByIds`, and `CatchRecordRepository` from Task 2.
- Produces: complete create/get/update/delete/list use cases for the HTTP layer.

- [ ] **Step 1: Write failing application-service tests**

Use recording fakes, as the favorites application tests do, and an injected fixed clock:

```java
private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-20T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

@Test
void createsARecordForTheAuthenticatedUserAndResolvedFish() {
    CatchRecordDetailView created = service.create(
            "angler@example.com",
            new CatchRecordCommand("channa-argus", LocalDate.parse("2026-08-20"),
                    "  城郊水库  ", new BigDecimal("42.5"),
                    new BigDecimal("1350"), " 路亚 ", " 傍晚近岸中鱼 "));

    assertThat(repository.saved.userId()).isEqualTo(41L);
    assertThat(repository.saved.details().fishId()).isEqualTo(1L);
    assertThat(created.fishSlug()).isEqualTo("channa-argus");
    assertThat(created.commonNameZh()).isEqualTo("乌鳢");
}

@Test
void hidesAnotherUsersRecordBehindTheSameNotFoundError() {
    repository.ownedRecord = Optional.empty();
    assertThatThrownBy(() -> service.get("angler@example.com", 99L))
            .isInstanceOfSatisfying(CatchRecordNotFoundException.class,
                    error -> assertThat(error.code()).isEqualTo("CATCH_RECORD_NOT_FOUND"));
}
```

Also cover: fixed page size 20; negative page; batched fish summary lookup preserving repository order; future date in Shanghai; update replacing all editable fields while preserving photo reference; delete not found; and invalid/noncanonical fish slug propagating a stable client error without saving.

- [ ] **Step 2: Run the application test and verify RED**

Run: `cd backend && ./mvnw -Dtest=DefaultCatchRecordApplicationServiceTest test`

Expected: compilation failure because the application types do not exist.

- [ ] **Step 3: Implement the service contracts and business clock**

Use this interface:

```java
public interface CatchRecordApplicationService {
    CatchRecordDetailView create(String authenticatedEmail, CatchRecordCommand command);
    CatchRecordPageView list(String authenticatedEmail, int page);
    CatchRecordDetailView get(String authenticatedEmail, long id);
    CatchRecordDetailView update(String authenticatedEmail, long id, CatchRecordCommand command);
    void delete(String authenticatedEmail, long id);
}
```

`DefaultCatchRecordApplicationService` must:

1. Resolve the current user only from `authenticatedEmail`.
2. Resolve `fishSlug` only through `FishCatalogQueryService`.
3. Calculate `LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")))` before domain validation.
4. Scope every repository call with `user.id()`.
5. Fetch catalog summaries in one batch for list responses and map them back without changing repository order.
6. Return `hasPhoto = record.photoObjectKey() != null` but never return the key.

Define one production bean:

```java
@Configuration
class CatchLogClockConfiguration {
    @Bean
    Clock catchLogClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
```

- [ ] **Step 4: Run the application test and verify GREEN**

Run: `cd backend && ./mvnw -Dtest=DefaultCatchRecordApplicationServiceTest test`

Expected: all application tests pass.

- [ ] **Step 5: Commit the application slice**

```bash
git add backend/src/main/java/com/fishbook/catchlog/application backend/src/test/java/com/fishbook/catchlog/application
git commit -m "feat: add catch record use cases"
```

---

### Task 4: Expose the Secure Catch Record HTTP API

**Files:**
- Create: `backend/src/main/java/com/fishbook/catchlog/web/CatchRecordController.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/web/dto/CatchRecordRequest.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/web/dto/CatchRecordSummaryResponse.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/web/dto/CatchRecordDetailResponse.java`
- Create: `backend/src/main/java/com/fishbook/catchlog/web/dto/CatchRecordPageResponse.java`
- Modify: `backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/fishbook/catchlog/web/CatchRecordApiIntegrationTest.java`
- Test: `backend/src/test/java/com/fishbook/catchlog/web/CatchRecordAuthorizationTest.java`

**Interfaces:**
- Consumes: `CatchRecordApplicationService` and view records from Task 3.
- Produces: the five `/api/v1/catches` endpoints and stable JSON errors consumed by the frontend.

- [ ] **Step 1: Write failing authorization and API contract tests**

Cover all methods, CSRF, ownership, paging, response shape, and errors. Representative assertions:

```java
@Test
void createReturnsLocationAndCompleteRecord() throws Exception {
    mvc.perform(post("/api/v1/catches")
                    .with(user(USER_EMAIL)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"fishSlug":"channa-argus","caughtOn":"2026-08-20",
                             "location":"城郊水库","lengthCm":42.5,"weightG":1350,
                             "method":"路亚","notes":"傍晚近岸中鱼"}
                            """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("/api/v1/catches/\\d+")))
            .andExpect(jsonPath("$.fishSlug").value("channa-argus"))
            .andExpect(jsonPath("$.commonNameZh").value("乌鳢"))
            .andExpect(jsonPath("$.hasPhoto").value(false));
}

@Test
void anotherUsersRecordUsesTheSameNotFoundContractForReadUpdateAndDelete() throws Exception {
    long id = insertCatchFor(OWNER_ID);
    mvc.perform(get("/api/v1/catches/{id}", id).with(user(OTHER_EMAIL)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CATCH_RECORD_NOT_FOUND"));
    mvc.perform(delete("/api/v1/catches/{id}", id).with(user(OTHER_EMAIL)).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CATCH_RECORD_NOT_FOUND"));
}
```

Authorization tests must assert anonymous GET/POST/PUT/DELETE return `401 AUTHENTICATION_REQUIRED`, while authenticated unsafe requests without CSRF return `403 CSRF_INVALID`.

- [ ] **Step 2: Run the web tests and verify RED**

Run: `cd backend && ./mvnw -Dtest=CatchRecordApiIntegrationTest,CatchRecordAuthorizationTest test`

Expected: requests fail because the routes are not registered.

- [ ] **Step 3: Implement request/response DTOs and controller**

Controller behavior:

```java
@RestController
@RequestMapping("/api/v1/catches")
class CatchRecordController {
    @PostMapping
    ResponseEntity<CatchRecordDetailResponse> create(
            Authentication authentication, @RequestBody CatchRecordRequest request) {
        CatchRecordDetailResponse response = CatchRecordDetailResponse.from(
                service.create(authentication.getName(), request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/catches/" + response.id()))
                .body(response);
    }

    @GetMapping
    CatchRecordPageResponse list(Authentication authentication,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        if (size != null) {
            throw new InvalidCatchRecordQueryException(
                    "size is fixed and must not be provided");
        }
        return CatchRecordPageResponse.from(
                service.list(authentication.getName(), parsePage(page)));
    }

    @GetMapping("/{id}")
    CatchRecordDetailResponse get(Authentication authentication, @PathVariable long id) {
        return CatchRecordDetailResponse.from(service.get(authentication.getName(), id));
    }

    @PutMapping("/{id}")
    CatchRecordDetailResponse update(Authentication authentication, @PathVariable long id,
            @RequestBody CatchRecordRequest request) {
        return CatchRecordDetailResponse.from(
                service.update(authentication.getName(), id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication authentication, @PathVariable long id) {
        service.delete(authentication.getName(), id);
    }

    private static int parsePage(String page) {
        if (page == null) return 0;
        try {
            return Integer.parseInt(page);
        } catch (NumberFormatException exception) {
            throw new InvalidCatchRecordQueryException(
                    "page must be a non-negative integer");
        }
    }
}
```

The request contains exactly `fishSlug`, `caughtOn`, `location`, `lengthCm`, `weightG`, `method`, and `notes`. Response DTOs include `id`, fish slug/name, record fields, `hasPhoto`, `createdAt`, and `updatedAt`; list summaries omit `notes`.

- [ ] **Step 4: Add security and stable error mappings**

Add `PathPatternRequestMatcher.pathPattern("/api/v1/catches/**")` to the existing authenticated endpoint matcher. Map `InvalidCatchRecordException` and `InvalidCatchRecordQueryException` to `400 INVALID_CATCH_RECORD`, and `CatchRecordNotFoundException` to `404 CATCH_RECORD_NOT_FOUND`; do not echo private notes or database details.

- [ ] **Step 5: Run focused and regression backend tests**

Run: `cd backend && ./mvnw -Dtest=CatchRecordApiIntegrationTest,CatchRecordAuthorizationTest,IdentityAuthorizationTest,IdentityErrorContractTest,FavoriteAuthorizationTest,FavoriteApiIntegrationTest test`

Expected: all selected tests pass.

- [ ] **Step 6: Commit the HTTP slice**

```bash
git add backend/src/main/java/com/fishbook/catchlog/web backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java backend/src/test/java/com/fishbook/catchlog/web
git commit -m "feat: expose secure catch record api"
```

---

### Task 5: Add the Frontend Catch API and Form Model

**Files:**
- Create: `frontend/src/features/catchlog/model/types.ts`
- Create: `frontend/src/features/catchlog/model/catchRecordForm.ts`
- Create: `frontend/src/features/catchlog/model/catchRecordForm.test.ts`
- Create: `frontend/src/features/catchlog/api/catchRecordsApi.ts`
- Create: `frontend/src/features/catchlog/api/catchRecordsApi.test.ts`
- Modify: `frontend/src/features/auth/api/sessionCache.ts`
- Modify: `frontend/src/features/auth/components/SessionNav.test.tsx`

**Interfaces:**
- Consumes: the HTTP JSON contract from Task 4 and the shared `apiFetch` client.
- Produces: typed query keys, CRUD calls, form schema, normalization, and session-scoped cache behavior for pages.

- [ ] **Step 1: Write failing API and form-model tests**

Specify exact request paths/bodies and normalization:

```typescript
test('creates a record with JSON through the shared CSRF client', async () => {
  const input: CatchRecordInput = {
    fishSlug: 'channa-argus', caughtOn: '2026-08-20', location: '城郊水库',
    lengthCm: 42.5, weightG: 1350, method: '路亚', notes: '傍晚近岸中鱼',
  };
  await createCatchRecord(input);
  expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/catches');
  expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
    method: 'POST', body: JSON.stringify(input), credentials: 'include',
  });
});

test('normalizes blank optional measurements and text to null', () => {
  const parsed = parseCatchForm({
    fishSlug: 'channa-argus', caughtOn: '2026-08-20', location: ' 水库 ',
    lengthCm: '', weightG: '', method: ' ', notes: '',
  }, '2026-08-20');
  expect(parsed).toEqual({
    fishSlug: 'channa-argus', caughtOn: '2026-08-20', location: '水库',
    lengthCm: null, weightG: null, method: null, notes: null,
  });
});
```

Also test page/detail query keys, page requests without `size`, GET/PUT/DELETE paths, future date, negative/overflow measurements, and all text lengths.

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `cd frontend && npm test -- src/features/catchlog/api/catchRecordsApi.test.ts src/features/catchlog/model/catchRecordForm.test.ts`

Expected: FAIL because the modules do not exist.

- [ ] **Step 3: Implement types, query keys, CRUD functions, and form parsing**

Use these core types and functions:

```typescript
export type CatchRecordInput = {
  fishSlug: string;
  caughtOn: string;
  location: string;
  lengthCm: number | null;
  weightG: number | null;
  method: string | null;
  notes: string | null;
};

export const CATCHES_QUERY_KEY = ['catches'] as const;
export const catchPageQueryKey = (page: number) => [...CATCHES_QUERY_KEY, 'page', page] as const;
export const catchDetailQueryKey = (id: number) => [...CATCHES_QUERY_KEY, 'detail', id] as const;

export const fetchCatchPage = (page: number) =>
  apiFetch<CatchRecordPage>(`/api/v1/catches?page=${page}`);
export const fetchCatchRecord = (id: number) =>
  apiFetch<CatchRecordDetail>(`/api/v1/catches/${id}`);
export const createCatchRecord = (input: CatchRecordInput) =>
  apiFetch<CatchRecordDetail>('/api/v1/catches', { method: 'POST', body: JSON.stringify(input) });
export const updateCatchRecord = (id: number, input: CatchRecordInput) =>
  apiFetch<CatchRecordDetail>(`/api/v1/catches/${id}`, { method: 'PUT', body: JSON.stringify(input) });
export const deleteCatchRecord = (id: number) =>
  apiFetch<void>(`/api/v1/catches/${id}`, { method: 'DELETE' });
```

`parseCatchForm(values, today)` must return a `CatchRecordInput` or throw a Zod error. Calculate `todayInShanghai()` with `new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())` rather than UTC string slicing.

- [ ] **Step 4: Include catches in session cache clearing**

`clearSessionScopedQueries` and `expireSessionOnUnauthorized` must remove `CATCHES_QUERY_KEY` before resetting/removing current-user data. Extend the existing auth tests with seeded catch list and detail data to prove no previous user's records can appear during logout, login, or session expiry.

- [ ] **Step 5: Run focused frontend tests and verify GREEN**

Run: `cd frontend && npm test -- src/features/catchlog src/features/auth/api src/features/auth/components/SessionNav.test.tsx src/features/auth/pages/LoginPage.test.tsx`

Expected: all selected tests pass.

- [ ] **Step 6: Commit the frontend foundation**

```bash
git add frontend/src/features/catchlog frontend/src/features/auth/api/sessionCache.ts frontend/src/features/auth/components/SessionNav.test.tsx frontend/src/features/auth/pages/LoginPage.test.tsx
git commit -m "feat: add catch record frontend api"
```

---

### Task 6: Deliver the Personal Catch List and Navigation

**Files:**
- Create: `frontend/src/features/catchlog/pages/CatchListPage.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchListPage.test.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchPages.module.css`
- Modify: `frontend/src/features/auth/components/SessionNav.tsx`
- Modify: `frontend/src/features/auth/components/SessionNav.test.tsx`
- Modify: `frontend/src/features/auth/hooks/useExpireSessionOnUnauthorized.ts`
- Modify: `frontend/src/features/catalog/pages/FishCatalogPage.tsx`
- Modify: `frontend/src/features/favorites/pages/FavoritesPage.tsx`
- Modify: `frontend/src/app/router.tsx`

**Interfaces:**
- Consumes: `fetchCatchPage`, `catchPageQueryKey`, and `CATCHES_QUERY_KEY` from Task 5.
- Produces: protected `/catches`, a logged-in “钓获记录” nav link, and reusable session-expiry handling.

- [ ] **Step 1: Write failing list-page and navigation tests**

Cover pending, empty, error/retry, populated summaries, page URL state, out-of-range page recovery, unauthorized cache expiry, and protected-route behavior:

```typescript
test('renders private catch summaries without per-row catalog requests', async () => {
  renderCatchList();
  expect(await screen.findByRole('heading', { name: '乌鳢' })).toBeInTheDocument();
  expect(screen.getByText('2026-08-20 · 城郊水库')).toBeInTheDocument();
  expect(screen.getByText('42.5 cm · 1350 g')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '查看这次钓获' }))
    .toHaveAttribute('href', '/catches/31');
});

test('authenticated navigation exposes catch records', () => {
  renderSessionNav();
  expect(screen.getByRole('link', { name: '钓获记录' })).toHaveAttribute('href', '/catches');
});
```

- [ ] **Step 2: Run the page tests and verify RED**

Run: `cd frontend && npm test -- src/features/catchlog/pages/CatchListPage.test.tsx src/features/auth/components/SessionNav.test.tsx`

Expected: FAIL because the page and link do not exist.

- [ ] **Step 3: Implement the list page and route**

The page must:

- keep `page` in `?page=N`, default malformed values to 0, and never send `size`;
- render server-provided fish names directly;
- display safe loading/error/empty states and a retry button;
- show previous/next controls only when pagination is needed;
- link to `/catches/new` and `/catches/{id}`;
- redirect to `/login` after a confirmed 401 and remove catch caches.

Rename the hook export from `useFavoriteSessionExpiry` to `useSessionExpiry`, update catalog/favorites imports, and reuse it for catch pages. This is a behavior-preserving rename covered by existing tests.

- [ ] **Step 4: Run list, auth, catalog, and favorites regressions**

Run: `cd frontend && npm test -- src/features/catchlog/pages/CatchListPage.test.tsx src/features/auth src/features/catalog/pages/FishCatalogPage.test.tsx src/features/favorites/pages/FavoritesPage.test.tsx`

Expected: all selected tests pass.

- [ ] **Step 5: Commit the list slice**

```bash
git add frontend/src/features/catchlog/pages frontend/src/features/auth frontend/src/features/catalog/pages/FishCatalogPage.tsx frontend/src/features/favorites/pages/FavoritesPage.tsx frontend/src/app/router.tsx
git commit -m "feat: show personal catch records"
```

---

### Task 7: Create Catch Records Through a Validated Form

**Files:**
- Create: `frontend/src/features/catchlog/components/CatchRecordForm.tsx`
- Create: `frontend/src/features/catchlog/components/CatchRecordForm.test.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchNewPage.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchNewPage.test.tsx`
- Modify: `frontend/src/app/router.tsx`

**Interfaces:**
- Consumes: `parseCatchForm`, `createCatchRecord`, existing `fetchFishPage`, and shared `FormField`.
- Produces: reusable catch form and protected `/catches/new` route; successful creation seeds the detail cache used by Task 8.

- [ ] **Step 1: Write failing component and creation-page tests**

```typescript
test('submits normalized values selected from catalog options', async () => {
  const onSubmit = vi.fn().mockResolvedValue(undefined);
  const { user } = renderForm({ onSubmit });
  await user.selectOptions(screen.getByLabelText('鱼种'), 'channa-argus');
  await user.type(screen.getByLabelText('钓获日期'), '2026-08-20');
  await user.type(screen.getByLabelText('地点'), ' 城郊水库 ');
  await user.click(screen.getByRole('button', { name: '保存记录' }));
  expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
    fishSlug: 'channa-argus', location: '城郊水库', lengthCm: null, weightG: null,
  }));
});

test('successful creation seeds detail cache and navigates to the record', async () => {
  createCatchRecordMock.mockResolvedValue(savedCatch);
  const { user, queryClient } = renderNewPage();
  await completeRequiredFields(user);
  await user.click(screen.getByRole('button', { name: '保存记录' }));
  expect(await screen.findByTestId('location')).toHaveTextContent('/catches/31');
  expect(queryClient.getQueryData(catchDetailQueryKey(31))).toEqual(savedCatch);
});
```

Also test every client validation message, catalog option loading/error/retry, duplicate submission disabling, server field errors when present, and a safe generic save failure that does not reveal backend details.

- [ ] **Step 2: Run the component/page tests and verify RED**

Run: `cd frontend && npm test -- src/features/catchlog/components/CatchRecordForm.test.tsx src/features/catchlog/pages/CatchNewPage.test.tsx`

Expected: FAIL because the form and page do not exist.

- [ ] **Step 3: Implement the reusable form**

`CatchRecordForm` accepts:

```typescript
type CatchRecordFormProps = {
  fishOptions: Array<{ slug: string; commonNameZh: string }>;
  initialValues?: CatchFormValues;
  submitLabel: string;
  onSubmit: (input: CatchRecordInput) => Promise<void>;
};
```

Use a `<select>` for fish; never accept a free-text slug. Use date/number inputs with matching min/max/step attributes, while treating Zod/backend validation as authoritative.

- [ ] **Step 4: Implement creation orchestration and protected route**

Fetch the existing catalog page with `{ q: '', family: '', habitat: '', page: 0 }`; the current catalog has 12 species and the public API's fixed page size is 12. On success, set `catchDetailQueryKey(created.id)`, invalidate `CATCHES_QUERY_KEY`, and navigate to `/catches/{id}`. On failure, remain on the form with entered values intact.

- [ ] **Step 5: Run creation tests and frontend type/lint checks**

Run: `cd frontend && npm test -- src/features/catchlog/components/CatchRecordForm.test.tsx src/features/catchlog/pages/CatchNewPage.test.tsx && npm run lint && npm run build`

Expected: tests, lint, and build pass.

- [ ] **Step 6: Commit the creation slice**

```bash
git add frontend/src/features/catchlog/components frontend/src/features/catchlog/pages/CatchNewPage.tsx frontend/src/features/catchlog/pages/CatchNewPage.test.tsx frontend/src/app/router.tsx
git commit -m "feat: create catch records"
```

---

### Task 8: View, Edit, and Delete Owned Catch Records

**Files:**
- Create: `frontend/src/features/catchlog/pages/CatchDetailPage.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchDetailPage.test.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchEditPage.tsx`
- Create: `frontend/src/features/catchlog/pages/CatchEditPage.test.tsx`
- Modify: `frontend/src/app/router.tsx`

**Interfaces:**
- Consumes: reusable form from Task 7 plus detail/update/delete API functions from Task 5.
- Produces: protected `/catches/{id}` and `/catches/{id}/edit` routes with complete no-photo CRUD.

- [ ] **Step 1: Write failing detail/edit/delete tests**

```typescript
test('shows all saved fields and the no-photo state', async () => {
  renderCatchDetail();
  expect(await screen.findByRole('heading', { name: '乌鳢钓获记录' })).toBeInTheDocument();
  expect(screen.getByText('城郊水库')).toBeInTheDocument();
  expect(screen.getByText('路亚')).toBeInTheDocument();
  expect(screen.getByText('傍晚近岸中鱼')).toBeInTheDocument();
  expect(screen.getByText('尚未添加照片')).toBeInTheDocument();
});

test('requires an explicit confirmation before deleting', async () => {
  const { user } = renderCatchDetail();
  await user.click(await screen.findByRole('button', { name: '删除记录' }));
  expect(deleteCatchRecordMock).not.toHaveBeenCalled();
  await user.click(screen.getByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(deleteCatchRecordMock).toHaveBeenCalledWith(31));
  expect(await screen.findByTestId('location')).toHaveTextContent('/catches');
});

test('updates all editable fields and returns to detail', async () => {
  updateCatchRecordMock.mockResolvedValue(updatedCatch);
  const { user, queryClient } = renderCatchEdit();
  await user.clear(await screen.findByLabelText('地点'));
  await user.type(screen.getByLabelText('地点'), '新地点');
  await user.click(screen.getByRole('button', { name: '保存修改' }));
  expect(queryClient.getQueryData(catchDetailQueryKey(31))).toEqual(updatedCatch);
  expect(await screen.findByTestId('location')).toHaveTextContent('/catches/31');
});
```

Also test loading/error/retry, malformed route IDs, `404 CATCH_RECORD_NOT_FOUND`, failed delete retaining the detail, cancel confirmation, empty optional fields, and expired-session cache removal.

- [ ] **Step 2: Run detail/edit tests and verify RED**

Run: `cd frontend && npm test -- src/features/catchlog/pages/CatchDetailPage.test.tsx src/features/catchlog/pages/CatchEditPage.test.tsx`

Expected: FAIL because the pages do not exist.

- [ ] **Step 3: Implement detail, deletion confirmation, and edit orchestration**

Use an in-page confirmation region with `role="alertdialog"`, “确认删除”, and “取消”; do not delete on the first click. Successful update sets the detail cache and invalidates list pages. Successful delete removes the detail query, invalidates list pages, and navigates to `/catches`; failed mutations show safe retryable status and keep the current record visible.

- [ ] **Step 4: Register protected detail/edit routes**

Add these route patterns, ordered explicitly:

```tsx
{
  path: '/catches/new',
  element: <ProtectedRoute><CatchNewPage /></ProtectedRoute>,
},
{
  path: '/catches/:id/edit',
  element: <ProtectedRoute><CatchEditPage /></ProtectedRoute>,
},
{
  path: '/catches/:id',
  element: <ProtectedRoute><CatchDetailPage /></ProtectedRoute>,
},
```

- [ ] **Step 5: Run all catch frontend tests and regressions**

Run: `cd frontend && npm test -- src/features/catchlog src/features/auth src/features/favorites src/features/catalog/pages && npm run lint && npm run build`

Expected: all selected tests, lint, and build pass.

- [ ] **Step 6: Commit the complete frontend CRUD slice**

```bash
git add frontend/src/features/catchlog/pages frontend/src/app/router.tsx
git commit -m "feat: manage catch record details"
```

---

### Task 9: Prove the Browser Flow and Publish Accurate Milestone Documentation

**Files:**
- Create: `e2e/tests/catches-flow.spec.ts`
- Modify: `README.md`

**Interfaces:**
- Consumes: the complete Docker-served application from Tasks 1–8.
- Produces: browser evidence for no-photo CRUD and README claims limited to implemented behavior.

- [ ] **Step 1: Write the failing Playwright flow**

```typescript
test('creates edits and deletes a private catch without a photo', async ({ page }) => {
  const uniqueId = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  await registerAndLogin(page, `catches-${uniqueId}@example.com`);

  await page.getByRole('link', { name: '钓获记录' }).click();
  await page.getByRole('link', { name: '记录一次钓获' }).click();
  await page.getByLabel('鱼种').selectOption('channa-argus');
  await page.getByLabel('钓获日期').fill(todayInShanghai());
  await page.getByLabel('地点').fill('城郊水库');
  await page.getByRole('button', { name: '保存记录' }).click();
  await expect(page.getByRole('heading', { name: '乌鳢钓获记录' })).toBeVisible();

  await page.getByRole('link', { name: '编辑记录' }).click();
  await page.getByLabel('地点').fill('河湾');
  await page.getByRole('button', { name: '保存修改' }).click();
  await expect(page.getByText('河湾')).toBeVisible();

  await page.getByRole('button', { name: '删除记录' }).click();
  await page.getByRole('button', { name: '确认删除' }).click();
  await expect(page.getByRole('heading', { name: '还没有钓获记录' })).toBeVisible();
});
```

Add a second browser/API context for another user and assert a direct GET to the first record ID returns `404 CATCH_RECORD_NOT_FOUND`.

- [ ] **Step 2: Run the new E2E test and verify RED before the full stack includes the feature**

Run: `cd e2e && npm test -- catches-flow.spec.ts`

Expected before rebuilding the implementation image: FAIL because the catch navigation/route is unavailable.

- [ ] **Step 3: Rebuild the full stack and verify GREEN**

Run: `docker compose --env-file .env.example -f compose.yaml -f compose.full.yaml up -d --build`

Run: `cd e2e && npm test -- catches-flow.spec.ts`

Expected: both CRUD and cross-user isolation flows pass.

- [ ] **Step 4: Update README claims in Chinese and English**

Document that identity, public catalog, private favorites, and no-photo catch-record CRUD are implemented. Add `/catches` and `/catches/new` to access URLs, name `catchlog` in architecture, and keep optional private MinIO photos as the next milestone. Do not claim photo upload or administrator workflows are complete.

- [ ] **Step 5: Run documentation and Compose checks**

Run: `test -f docs/superpowers/specs/2026-08-14-personal-product-loop-design.md`

Run: `docker compose --env-file .env.example -f compose.yaml -f compose.full.yaml config --quiet`

Run: `git diff --check`

Expected: every command exits 0.

- [ ] **Step 6: Commit browser proof and docs**

```bash
git add e2e/tests/catches-flow.spec.ts README.md
git commit -m "test: verify catch record product flow"
```

---

### Task 10: Run the Milestone-Two Release Gate

**Files:**
- Verify only; change files only through a new failing test if this gate exposes a defect.

**Interfaces:**
- Consumes: all preceding tasks.
- Produces: fresh evidence that milestone two is complete without regressing identity, catalog, or favorites.

- [ ] **Step 1: Run the complete backend suite**

Run: `cd backend && ./mvnw test`

Expected: all backend tests pass against Testcontainers MySQL.

- [ ] **Step 2: Run the complete frontend gate**

Run: `cd frontend && npm run lint && npm test -- --run && npm run build`

Expected: lint, every Vitest test, TypeScript compilation, and Vite production build pass.

- [ ] **Step 3: Run the complete browser suite**

Run: `cd e2e && npm test`

Expected: identity, catalog, favorites, and catches Playwright flows pass.

- [ ] **Step 4: Verify repository and container configuration**

Run: `docker compose --env-file .env.example -f compose.yaml -f compose.full.yaml config --quiet`

Run: `git diff --check`

Run: `git status --short`

Expected: Compose and diff checks exit 0; status contains no unintended or generated files.

- [ ] **Step 5: Record the final implementation state**

Run: `git log --oneline --decorate -10`

Expected: domain, persistence, application, HTTP, frontend, and browser/documentation commits are present on `codex/catch-records-core`.
