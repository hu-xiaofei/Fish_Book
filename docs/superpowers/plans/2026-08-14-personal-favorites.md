# Personal Favorites Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver an authenticated, user-isolated, idempotent fish favorites flow from catalog cards and details through a paginated “我的收藏” page.

**Architecture:** Add a `favorites` vertical module with a MySQL repository port and application service. The module resolves authenticated users through `ProfileApplicationService` and fish references/summaries through new public `FishCatalogQueryService` batch methods; it never accesses another module’s Spring Data repository. React uses one batch status request per visible catalog page and invalidates the `['favorites']` query-key prefix after writes.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Security/Session/CSRF, Spring Data JPA, MySQL 8.4, Flyway, React 19, TypeScript 5.9, TanStack Query 5, React Router 7, Vitest/Testing Library, Playwright.

## Global Constraints

- Follow `docs/superpowers/specs/2026-08-14-personal-product-loop-design.md`.
- All favorites endpoints require authentication; all writes require the existing CSRF token flow.
- Obtain the current user from `Authentication.getName()` and `ProfileApplicationService`; never accept `userId` from the client.
- `PUT` favorite and `DELETE` favorite are idempotent under concurrent requests.
- Favorites pages are zero-based, fixed at 12 items, and reject an explicit `size` parameter.
- Batch status accepts at most 12 unique canonical slugs and must not create one request per card.
- Keep controllers out of Spring Data repositories, never expose JPA entities, and keep Catalog as the owner of fish summaries.
- Use TDD, run the named focused test before and after each implementation, and commit only the files named by the task.

## File and Responsibility Map

**Catalog public query additions**

- Modify `backend/src/main/java/com/fishbook/catalog/domain/FishRepository.java`: batch lookup ports.
- Modify `backend/src/main/java/com/fishbook/catalog/persistence/SpringDataFishSpeciesJpaRepository.java`: detail-fetch queries by IDs and slugs.
- Modify `backend/src/main/java/com/fishbook/catalog/persistence/JpaFishRepositoryAdapter.java`: preserve requested order while mapping batch results.
- Create `backend/src/main/java/com/fishbook/catalog/application/FishReferenceView.java`: stable cross-module `(id, slug)` reference.
- Modify `backend/src/main/java/com/fishbook/catalog/application/FishCatalogQueryService.java`: public reference and summary methods.
- Modify `backend/src/main/java/com/fishbook/catalog/application/DefaultFishCatalogQueryService.java`: validation and mapping.

**Favorites backend**

- Create `backend/src/main/resources/db/migration/V6__create_favorites.sql`: table, constraints, and indexes.
- Create `backend/src/main/java/com/fishbook/favorites/domain/{FavoriteEntry,FavoritePage,FavoriteRepository}.java`: persistence-neutral port types.
- Create `backend/src/main/java/com/fishbook/favorites/persistence/{FavoriteJpaEntity,SpringDataFavoriteJpaRepository,JpaFavoriteRepositoryAdapter}.java`: MySQL implementation.
- Create `backend/src/main/java/com/fishbook/favorites/application/{FavoriteApplicationService,DefaultFavoriteApplicationService,FavoritePageView,FavoriteSummaryView,FavoriteStatusView,InvalidFavoriteQueryException}.java`: use cases and views.
- Create `backend/src/main/java/com/fishbook/favorites/web/FavoriteController.java` and explicit page, summary, status-container, and status-item DTOs: HTTP boundary.
- Modify `backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java`: stable favorite query error.
- Modify `backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java`: authenticate `/api/v1/favorites/**`.

**Favorites frontend**

- Create `frontend/src/features/favorites/model/types.ts`: API types.
- Create `frontend/src/features/favorites/api/favoritesApi.ts`: query keys and HTTP functions.
- Create `frontend/src/features/favorites/components/FavoriteButton.tsx`: reusable authenticated/anonymous interaction.
- Create `frontend/src/features/favorites/pages/FavoritesPage.tsx` and `.module.css`: personal list.
- Modify catalog cards/pages, login return navigation, router, and session navigation.

**Tests and docs**

- Add focused backend migration, repository, application, Web/security tests under `backend/src/test/java/com/fishbook/favorites`.
- Add frontend API/component/page tests beside the new files and modify affected catalog/login tests.
- Create `e2e/tests/favorites-flow.spec.ts`.
- Modify `README.md` after the browser flow passes.

---

### Task 1: Publish Catalog Batch Reference and Summary Queries

**Files:**
- Create: `backend/src/main/java/com/fishbook/catalog/application/FishReferenceView.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/domain/FishRepository.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/persistence/SpringDataFishSpeciesJpaRepository.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/persistence/JpaFishRepositoryAdapter.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/application/FishCatalogQueryService.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/application/DefaultFishCatalogQueryService.java`
- Test: `backend/src/test/java/com/fishbook/catalog/application/DefaultFishCatalogQueryServiceTest.java`
- Test: `backend/src/test/java/com/fishbook/catalog/persistence/JpaFishRepositoryAdapterTest.java`

**Interfaces:**
- Produces: `FishReferenceView(long id, String slug)`.
- Produces on `FishCatalogQueryService`: `getReferenceBySlug(String)`, `getReferencesBySlugs(List<String>)`, and `getSummariesByIds(List<Long>)`.
- Contract: every batch result follows input order; missing slugs raise `FishNotFoundException`; missing IDs raise `IllegalStateException` because foreign keys should make that state impossible.

- [ ] **Step 1: Write failing application tests for ordered batch mapping**

Add tests that pass `List.of("channa-argus", "cyprinus-carpio")` and assert ordered `FishReferenceView` results, then pass fish IDs in reverse display order and assert summaries follow the requested ID order. Add a missing-slug assertion:

```java
assertThatThrownBy(() -> service.getReferencesBySlugs(List.of("missing-fish")))
        .isInstanceOf(FishNotFoundException.class);
```

- [ ] **Step 2: Run the application test and verify RED**

Run: `cd backend && ./mvnw -Dtest=DefaultFishCatalogQueryServiceTest test`

Expected: compilation failure because the three batch methods and `FishReferenceView` do not exist.

- [ ] **Step 3: Add exact ports and ordered implementations**

Add to `FishRepository`:

```java
List<FishSpecies> findAllByIds(List<Long> ids);
List<FishSpecies> findAllBySlugs(List<String> slugs);
```

Add to `FishCatalogQueryService`:

```java
FishReferenceView getReferenceBySlug(String slug);
List<FishReferenceView> getReferencesBySlugs(List<String> slugs);
List<FishSummaryView> getSummariesByIds(List<Long> ids);
```

Use `findAllWithDetailsByIdIn` and a new `findAllWithDetailsBySlugIn` entity-graph query in the Spring Data repository. In the adapter and service, map results by ID or slug and then iterate the input list so database ordering never leaks into the contract. Reject null lists, null elements, and duplicate slugs with `InvalidCatalogQueryException`.

- [ ] **Step 4: Add repository integration assertions and verify GREEN**

Extend `JpaFishRepositoryAdapterTest` to request IDs/slugs in reverse order and assert exact order and complete aliases/habitats. Run:

`cd backend && ./mvnw -Dtest=DefaultFishCatalogQueryServiceTest,JpaFishRepositoryAdapterTest test`

Expected: all selected tests pass.

- [ ] **Step 5: Commit the Catalog capability**

```bash
git add backend/src/main/java/com/fishbook/catalog backend/src/test/java/com/fishbook/catalog
git commit -m "feat: expose catalog batch references"
```

### Task 2: Create Favorites Schema and Idempotent Repository

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__create_favorites.sql`
- Create: `backend/src/main/java/com/fishbook/favorites/domain/FavoriteEntry.java`
- Create: `backend/src/main/java/com/fishbook/favorites/domain/FavoritePage.java`
- Create: `backend/src/main/java/com/fishbook/favorites/domain/FavoriteRepository.java`
- Create: `backend/src/main/java/com/fishbook/favorites/persistence/FavoriteJpaEntity.java`
- Create: `backend/src/main/java/com/fishbook/favorites/persistence/SpringDataFavoriteJpaRepository.java`
- Create: `backend/src/main/java/com/fishbook/favorites/persistence/JpaFavoriteRepositoryAdapter.java`
- Create: `backend/src/test/java/com/fishbook/favorites/persistence/FavoriteDatabaseMigrationTest.java`
- Create: `backend/src/test/java/com/fishbook/favorites/persistence/JpaFavoriteRepositoryAdapterTest.java`

**Interfaces:**
- Produces: `FavoriteEntry(long fishId, Instant favoritedAt)`.
- Produces: `FavoritePage(List<FavoriteEntry> items, int page, int size, long totalItems, int totalPages)`.
- Produces on `FavoriteRepository`: idempotent `add`, `remove`, paginated `findByUserId`, and batch `findFavoritedFishIds`.

- [ ] **Step 1: Write the failing migration test**

Assert `favorites` exists with `uk_favorites_user_fish`, foreign keys `fk_favorites_user` and `fk_favorites_fish`, plus `ix_favorites_user_created_id`. Insert two rows for the same user/fish and assert the second insert fails.

- [ ] **Step 2: Run the migration test and verify RED**

Run: `cd backend && ./mvnw -Dtest=FavoriteDatabaseMigrationTest test`

Expected: failure because Flyway has not created `favorites`.

- [ ] **Step 3: Create V6 with exact constraints**

Use this table shape:

```sql
CREATE TABLE favorites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    fish_species_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_favorites PRIMARY KEY (id),
    CONSTRAINT uk_favorites_user_fish UNIQUE (user_id, fish_species_id),
    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorites_fish FOREIGN KEY (fish_species_id)
        REFERENCES fish_species (id) ON DELETE RESTRICT,
    INDEX ix_favorites_user_created_id (user_id, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- [ ] **Step 4: Write the repository test and verify RED**

Define the port:

```java
public interface FavoriteRepository {
    void add(long userId, long fishId, Instant now);
    void remove(long userId, long fishId);
    FavoritePage findByUserId(long userId, int page, int size);
    Set<Long> findFavoritedFishIds(long userId, Set<Long> fishIds);
}
```

Test two calls to `add` create one row, two calls to `remove` succeed, pages sort by `created_at DESC, id DESC`, and the status lookup returns only the current user’s fish IDs.

Run: `cd backend && ./mvnw -Dtest=JpaFavoriteRepositoryAdapterTest test`

Expected: compilation failure because the repository types do not exist.

- [ ] **Step 5: Implement the minimal MySQL adapter and verify GREEN**

Map only scalar `user_id` and `fish_species_id` fields in `FavoriteJpaEntity`. Use a native modifying query with MySQL `INSERT IGNORE` for concurrent idempotency:

```java
@Modifying
@Query(value = "INSERT IGNORE INTO favorites(user_id, fish_species_id, created_at) "
        + "VALUES (:userId, :fishId, :createdAt)", nativeQuery = true)
int insertIfAbsent(long userId, long fishId, Instant createdAt);
```

Use `deleteByUserIdAndFishSpeciesId`, `findByUserId(Pageable)`, and `findAllByUserIdAndFishSpeciesIdIn` for the other operations. Run:

`cd backend && ./mvnw -Dtest=FavoriteDatabaseMigrationTest,JpaFavoriteRepositoryAdapterTest test`

Expected: all selected tests pass.

- [ ] **Step 6: Commit schema and repository**

```bash
git add backend/src/main/resources/db/migration/V6__create_favorites.sql backend/src/main/java/com/fishbook/favorites backend/src/test/java/com/fishbook/favorites
git commit -m "feat: persist personal favorites"
```

### Task 3: Implement Favorites Application Use Cases

**Files:**
- Create: `backend/src/main/java/com/fishbook/favorites/application/FavoriteApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/favorites/application/DefaultFavoriteApplicationService.java`
- Create: `backend/src/main/java/com/fishbook/favorites/application/FavoriteSummaryView.java`
- Create: `backend/src/main/java/com/fishbook/favorites/application/FavoritePageView.java`
- Create: `backend/src/main/java/com/fishbook/favorites/application/FavoriteStatusView.java`
- Create: `backend/src/main/java/com/fishbook/favorites/application/InvalidFavoriteQueryException.java`
- Create: `backend/src/test/java/com/fishbook/favorites/application/DefaultFavoriteApplicationServiceTest.java`

**Interfaces:**
- Consumes: `ProfileApplicationService.currentUser(email)`, Catalog batch methods from Task 1, and `FavoriteRepository` from Task 2.
- Produces: email-based use cases safe for controllers to call directly.

- [ ] **Step 1: Write failing use-case tests**

Cover add/remove resolving the authenticated email to user ID, list mapping fish summaries in favorite order, status preserving first-seen input order, duplicate slug de-duplication, page `-1`, explicit size, and 13 unique slugs. Assert invalid queries raise `InvalidFavoriteQueryException` with code `INVALID_FAVORITE_QUERY`.

- [ ] **Step 2: Run the test and verify RED**

Run: `cd backend && ./mvnw -Dtest=DefaultFavoriteApplicationServiceTest test`

Expected: compilation failure because the application API does not exist.

- [ ] **Step 3: Implement exact use-case signatures**

```java
public interface FavoriteApplicationService {
    void add(String authenticatedEmail, String fishSlug);
    void remove(String authenticatedEmail, String fishSlug);
    FavoritePageView list(String authenticatedEmail, int page);
    List<FavoriteStatusView> statuses(String authenticatedEmail, List<String> fishSlugs);
}
```

Use `PAGE_SIZE = 12`. `FavoriteSummaryView` contains the existing public fish summary fields plus `Instant favoritedAt`; `FavoritePageView` contains `items`, `page`, `size`, `totalItems`, and `totalPages`; `FavoriteStatusView` contains `fishSlug` and `favorited`. Normalize slugs by de-duplicating in first-seen order, require canonical lowercase slug syntax, reject more than 12 unique values, and never accept a client user ID.

- [ ] **Step 4: Verify GREEN**

Run: `cd backend && ./mvnw -Dtest=DefaultFavoriteApplicationServiceTest test`

Expected: all tests pass, including the 12-item status limit and ordered mapping.

- [ ] **Step 5: Commit application use cases**

```bash
git add backend/src/main/java/com/fishbook/favorites/application backend/src/test/java/com/fishbook/favorites/application
git commit -m "feat: add favorite use cases"
```

### Task 4: Expose Authenticated Favorites HTTP API

**Files:**
- Create: `backend/src/main/java/com/fishbook/favorites/web/FavoriteController.java`
- Create: `backend/src/main/java/com/fishbook/favorites/web/dto/FavoriteSummaryResponse.java`
- Create: `backend/src/main/java/com/fishbook/favorites/web/dto/FavoritePageResponse.java`
- Create: `backend/src/main/java/com/fishbook/favorites/web/dto/FavoriteStatusResponse.java`
- Create: `backend/src/main/java/com/fishbook/favorites/web/dto/FavoriteStatusItemResponse.java`
- Modify: `backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java`
- Create: `backend/src/test/java/com/fishbook/favorites/web/FavoriteApiIntegrationTest.java`
- Create: `backend/src/test/java/com/fishbook/favorites/web/FavoriteAuthorizationTest.java`

**Interfaces:**
- Produces: the four endpoints specified in the approved design.
- Response contract: writes return 204; list returns a page; status returns `{ "items": [{ "fishSlug": "...", "favorited": true }] }`.

- [ ] **Step 1: Write failing HTTP and security tests**

Test authenticated `PUT` twice leaves one row, `DELETE` twice returns 204, list returns only the current user’s rows, status handles repeated `fishSlug` query parameters, invalid page/size/13 slugs returns `INVALID_FAVORITE_QUERY`, missing fish returns `FISH_NOT_FOUND`, anonymous reads/writes return 401, and authenticated writes without CSRF return `CSRF_INVALID`.

- [ ] **Step 2: Run the tests and verify RED**

Run: `cd backend && ./mvnw -Dtest=FavoriteApiIntegrationTest,FavoriteAuthorizationTest test`

Expected: 403/404 failures because the endpoints are not routed or authenticated yet.

- [ ] **Step 3: Implement controller, DTOs, errors, and security matcher**

Use controller methods shaped as:

```java
@PutMapping("/{fishSlug}")
@ResponseStatus(HttpStatus.NO_CONTENT)
void add(Authentication auth, @PathVariable String fishSlug) { ... }

@DeleteMapping("/{fishSlug}")
@ResponseStatus(HttpStatus.NO_CONTENT)
void remove(Authentication auth, @PathVariable String fishSlug) { ... }

@GetMapping
FavoritePageResponse list(Authentication auth, @RequestParam(required = false) String page,
                          @RequestParam(required = false) String size) { ... }

@GetMapping("/status")
FavoriteStatusResponse statuses(Authentication auth,
        @RequestParam("fishSlug") List<String> fishSlugs) { ... }
```

Parse absent page as `0`; reject explicit `size`. Add `/api/v1/favorites/**` to `authenticationRequiredEndpoints` so anonymous access receives 401 rather than the deny-all 403. Add a GlobalExceptionHandler method mapping `InvalidFavoriteQueryException` to HTTP 400 and its stable code.

- [ ] **Step 4: Verify focused and identity/catalog regression tests**

Run:

`cd backend && ./mvnw -Dtest=FavoriteApiIntegrationTest,FavoriteAuthorizationTest,AuthFlowIntegrationTest,CatalogAuthorizationTest test`

Expected: all selected tests pass.

- [ ] **Step 5: Commit the Web boundary**

```bash
git add backend/src/main/java/com/fishbook/favorites/web backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java backend/src/test/java/com/fishbook/favorites
git commit -m "feat: expose favorites api"
```

### Task 5: Add Frontend Favorite API, Batch Status, and Button

**Files:**
- Create: `frontend/src/features/favorites/model/types.ts`
- Create: `frontend/src/features/favorites/api/favoritesApi.ts`
- Create: `frontend/src/features/favorites/api/favoritesApi.test.ts`
- Create: `frontend/src/features/favorites/components/FavoriteButton.tsx`
- Create: `frontend/src/features/favorites/components/FavoriteButton.test.tsx`
- Modify: `frontend/src/features/catalog/components/FishCard.tsx`
- Modify: `frontend/src/features/catalog/pages/FishCatalogPage.tsx`
- Modify: `frontend/src/features/catalog/pages/FishCatalogPage.test.tsx`
- Modify: `frontend/src/features/catalog/pages/FishDetailPage.tsx`
- Modify: `frontend/src/features/catalog/pages/FishDetailPage.test.tsx`
- Modify: `frontend/src/features/auth/pages/LoginPage.tsx`
- Modify: `frontend/src/features/auth/pages/LoginPage.test.tsx`

**Interfaces:**
- Produces: `favoriteStatusQueryKey(slugs)`, `fetchFavoriteStatuses`, `addFavorite`, `removeFavorite`, and `FavoriteButton`.
- `FavoriteButton` props: `{ fishSlug: string; isFavorited: boolean; returnTo: string }`.

- [ ] **Step 1: Write failing API tests**

Assert status builds repeated encoded `fishSlug` parameters in sorted query-key order but preserves request order, PUT/DELETE use encoded slugs, and all requests include credentials through `apiFetch`.

```ts
expect(favoriteStatusQueryKey(['b', 'a'])).toEqual(['favorites', 'status', 'a', 'b']);
```

Run: `cd frontend && npm test -- src/features/favorites/api/favoritesApi.test.ts`

Expected: module-not-found failure.

- [ ] **Step 2: Implement API types and functions**

Define:

```ts
export type FavoriteStatus = { fishSlug: string; favorited: boolean };
export type FavoriteStatusResponse = { items: FavoriteStatus[] };
export const FAVORITES_QUERY_KEY = ['favorites'] as const;
```

Implement one status GET for the visible slugs and `PUT`/`DELETE` returning `Promise<void>`.

- [ ] **Step 3: Write failing button, catalog, detail, and login-return tests**

Assert an authenticated button toggles and invalidates `['favorites']`; an anonymous button navigates to `/login?returnTo=<encoded current path>`; the catalog makes one status request for 12 cards; detail requests one slug; and successful login only accepts a same-origin path beginning with one `/` (reject `//evil.example`).

- [ ] **Step 4: Run UI tests and verify RED**

Run:

`cd frontend && npm test -- src/features/favorites/components/FavoriteButton.test.tsx src/features/catalog/pages/FishCatalogPage.test.tsx src/features/catalog/pages/FishDetailPage.test.tsx src/features/auth/pages/LoginPage.test.tsx`

Expected: missing component/API and return-target assertions fail.

- [ ] **Step 5: Implement the button and one-request batch wiring**

Use the existing current-user query to decide authenticated versus anonymous behavior. On mutation success invalidate `{ queryKey: FAVORITES_QUERY_KEY }`. In `FishCatalogPage`, enable the status query only when current-user data and fish data exist, then pass each boolean to `FishCard`; do not mount a status query inside every card. In `FishDetailPage`, query one slug. Parse login `returnTo` with:

```ts
const safeReturnTo = value?.startsWith('/') && !value.startsWith('//') ? value : '/profile';
```

- [ ] **Step 6: Verify GREEN and lint**

Run:

`cd frontend && npm test -- src/features/favorites src/features/catalog/pages/FishCatalogPage.test.tsx src/features/catalog/pages/FishDetailPage.test.tsx src/features/auth/pages/LoginPage.test.tsx && npm run lint`

Expected: all selected tests and lint pass.

- [ ] **Step 7: Commit favorite interaction**

```bash
git add frontend/src/features/favorites frontend/src/features/catalog frontend/src/features/auth/pages/LoginPage.tsx frontend/src/features/auth/pages/LoginPage.test.tsx
git commit -m "feat: add catalog favorite controls"
```

### Task 6: Build the “我的收藏” Page and Navigation

**Files:**
- Modify: `frontend/src/features/favorites/model/types.ts`
- Modify: `frontend/src/features/favorites/api/favoritesApi.ts`
- Create: `frontend/src/features/favorites/pages/FavoritesPage.tsx`
- Create: `frontend/src/features/favorites/pages/FavoritesPage.module.css`
- Create: `frontend/src/features/favorites/pages/FavoritesPage.test.tsx`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/features/auth/components/SessionNav.tsx`
- Modify: `frontend/src/features/auth/components/SessionNav.test.tsx`

**Interfaces:**
- Produces: `favoritePageQueryKey(page)` and `fetchFavoritePage(page)`.
- Produces protected route `/favorites`.

- [ ] **Step 1: Write failing page and navigation tests**

Cover loading, empty, populated, safe error/retry, previous/next pagination, remove-from-list invalidation, protected route, and the authenticated “我的收藏” navigation link. Do not add a `/catches` link until Plan 2 creates that route.

- [ ] **Step 2: Run and verify RED**

Run: `cd frontend && npm test -- src/features/favorites/pages/FavoritesPage.test.tsx src/features/auth/components/SessionNav.test.tsx`

Expected: missing page/route/link failures.

- [ ] **Step 3: Implement the page with existing catalog card vocabulary**

Render fish summaries without duplicating Catalog API calls, show `收藏于 <date>` from `favoritedAt`, retain accessible loading/error/empty states, and use fixed page metadata from the response. Wrap `/favorites` in `ProtectedRoute`. Do not add tags, sorting controls, or search.

- [ ] **Step 4: Verify frontend regression**

Run: `cd frontend && npm test && npm run lint && npm run build`

Expected: all frontend tests, lint, and TypeScript/Vite build pass.

- [ ] **Step 5: Commit the personal page**

```bash
git add frontend/src/features/favorites frontend/src/app/router.tsx frontend/src/features/auth/components
git commit -m "feat: add personal favorites page"
```

### Task 7: Prove the Favorite Flow End to End and Document It

**Files:**
- Create: `e2e/tests/favorites-flow.spec.ts`
- Modify: `README.md`

**Interfaces:**
- Produces: executable browser acceptance evidence for milestone one.

- [ ] **Step 1: Write the failing Playwright flow**

Register a unique user, log in, search “黑鱼”, add 乌鳢 to favorites, open `/favorites`, assert 乌鳢 appears, remove it, assert the empty state, reload, and assert it remains empty. Use role/name locators, not CSS selectors.

- [ ] **Step 2: Start the stack and run fresh browser acceptance**

Run:

```bash
docker compose -f compose.yaml -f compose.full.yaml up -d --build
cd e2e && npm test -- favorites-flow.spec.ts
```

Expected after Tasks 1–6: the scenario passes. If it fails, preserve the failing assertion, invoke `superpowers:systematic-debugging`, and add a focused backend or frontend regression test for that exact behavior before changing production code.

- [ ] **Step 3: Update README after acceptance passes**

Update README current features and test description to state that authenticated users can manage private favorites; do not claim catches or photos yet.

- [ ] **Step 4: Run the milestone completion gate**

Run:

```bash
cd backend && ./mvnw test
cd ../frontend && npm run lint && npm test && npm run build
cd ../e2e && npm test
cd .. && docker compose --env-file .env.example -f compose.yaml -f compose.full.yaml config --quiet
```

Expected: every command exits 0.

- [ ] **Step 5: Commit milestone one**

```bash
git add e2e/tests/favorites-flow.spec.ts README.md frontend backend
git commit -m "test: verify personal favorites flow"
```

## Plan 1 Completion Gate

- The milestone completion command in Task 7 exits 0.
- `git status --short` is empty.
- The API remains anonymous only for GET catalog endpoints; favorites return 401 when anonymous.
- A database query confirms no duplicate `(user_id, fish_species_id)` pairs.
- Continue with `docs/superpowers/plans/2026-08-14-catch-records-core.md` only after this gate passes.
