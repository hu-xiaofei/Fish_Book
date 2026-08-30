# Minimal Administration Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the smallest secure FishBook administration loop so a bootstrapped administrator can create, edit, publish, unpublish, and republish fish while public catalog users see only published content.

**Architecture:** Keep fish data and publication rules inside `catalog`; add an `administration` application/Web boundary that writes through a dedicated catalog management port. Reuse the existing identity, Session, CSRF, React Query, form, and error infrastructure, while making public lookups, internal historical-reference lookups, and administrator lookups explicit rather than hiding visibility rules in generic repository methods.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Security 7.1, Spring Data JPA, MySQL 8.4, Flyway, React 19.2, TypeScript 5.9, React Router 7.18, TanStack Query 5.101, React Hook Form 7.85, Zod 4.4, JUnit, Testcontainers, Vitest, Testing Library, Playwright 1.62, Docker Compose

**Spec:** `docs/superpowers/specs/2026-08-30-minimal-administration-loop-design.md`

## Global Constraints

- Work in strict RED → GREEN → REFACTOR cycles: write one focused failing test, run it and observe the expected failure, implement only enough production code to pass, then rerun the focused suite.
- Add no backend or frontend dependencies; use the versions already pinned by Maven and npm lockfiles.
- Keep `catalog.domain` free of Spring, JPA, Web, Jackson, and validation annotations.
- Existing fish rows migrate to `PUBLISHED`; new administrator-created fish always start as `DRAFT`.
- The only allowed transitions are `DRAFT → PUBLISHED`, `PUBLISHED → UNPUBLISHED`, and `UNPUBLISHED → PUBLISHED`.
- Content edits never alter publication status. Publishing updates `publishedAt`; unpublishing retains the most recent publish timestamp.
- Public list, filter, detail, favorite-add/status, new-catch, and changed-fish catch-update lookups accept only `PUBLISHED` fish.
- Catch create/edit selectors load every published catalog page; administrator-created fish must not become unreachable merely because the public catalog exceeds 12 rows.
- Existing favorites and catches continue resolving unpublished fish. Removing an existing favorite and retaining the same fish while editing an existing catch must remain possible.
- Fish `id`, `slug`, publication status, publish timestamp, and audit timestamps cannot be changed through the content update request.
- Create and update require complete content. Text columns use their schema lengths; the five MySQL `TEXT` content fields are capped at 10,000 Unicode code points so valid input fits `utf8mb4` storage.
- Administrator list pages contain exactly 20 rows, are zero-based, and reject a client-supplied `size` parameter.
- `/api/v1/admin/**` requires `ROLE_ADMIN`; every unsafe request requires the existing CSRF token.
- Anonymous management requests return `401 AUTHENTICATION_REQUIRED`; authenticated non-admin requests return `403 ACCESS_DENIED`.
- Do not add cover upload, public-media management, physical deletion, partial drafts, autosave, rich text, version history, concurrency control, RBAC tables, invitations, batch actions, dashboards, or review workflows.
- Never log or commit a real administrator password. Bootstrap is disabled by default and refuses to upgrade an existing `USER` account.
- Preserve all existing identity, public catalog, favorites, catches, private photo, media cleanup, Docker, and CI behavior. Do not delete Docker volumes during verification.

## Scope Decision

This remains one implementation plan because bootstrap, publication persistence, management writes, and the React workflow are sequential layers of one acceptance loop. Splitting them into separate plans would leave intermediate branches with either unreachable management APIs or a UI that cannot complete the approved lifecycle; the eight tasks below are the reviewer-sized boundaries.

## Planned File Structure

### Catalog domain and persistence

- `backend/src/main/resources/db/migration/V9__add_fish_publication_status.sql`: publication columns, constraint, backfill, and public-query index.
- `backend/src/main/java/com/fishbook/catalog/domain/PublicationStatus.java`: three stable persistence/API status names.
- `backend/src/main/java/com/fishbook/catalog/domain/InvalidFishSpeciesException.java`: safe domain-validation failure for administrator input.
- `backend/src/main/java/com/fishbook/catalog/domain/FishSpeciesContent.java`: complete editable fish content and length/invariant validation.
- `backend/src/main/java/com/fishbook/catalog/domain/FishSpecies.java`: aggregate identity, immutable slug, audit timestamps, and publication transitions.
- `backend/src/main/java/com/fishbook/catalog/domain/InvalidPublicationTransitionException.java`: stable conflict code.
- `backend/src/main/java/com/fishbook/catalog/domain/FishManagementSearchCriteria.java`: administrator query criteria.
- `backend/src/main/java/com/fishbook/catalog/domain/FishManagementRepository.java`: all-status list/detail/save port.
- `backend/src/main/java/com/fishbook/catalog/domain/FishRepository.java`: explicitly public and internal-reference read methods.
- `backend/src/main/java/com/fishbook/catalog/persistence/FishSpeciesJpaEntity.java`: publication mapping plus aggregate child replacement.
- `backend/src/main/java/com/fishbook/catalog/persistence/FishAliasJpaEntity.java`: writable alias child constructor.
- `backend/src/main/java/com/fishbook/catalog/persistence/FishHabitatId.java`: writable habitat key constructor.
- `backend/src/main/java/com/fishbook/catalog/persistence/FishHabitatJpaEntity.java`: writable habitat child constructor.
- `backend/src/main/java/com/fishbook/catalog/persistence/SpringDataFishSpeciesJpaRepository.java`: published, internal, and management queries.
- `backend/src/main/java/com/fishbook/catalog/persistence/JpaFishRepositoryAdapter.java`: implements public and management ports.

### Identity bootstrap and security

- `backend/src/main/java/com/fishbook/common/time/ApplicationClockConfiguration.java`: application-wide `Clock` bean shared by catches, bootstrap, and administration.
- `backend/src/main/java/com/fishbook/identity/domain/IdentityInputValidator.java`: shared email, password, and nickname validation.
- `backend/src/main/java/com/fishbook/identity/domain/User.java`: explicit administrator bootstrap factory.
- `backend/src/main/java/com/fishbook/identity/application/DefaultAuthApplicationService.java`: delegates registration validation to the shared validator.
- `backend/src/main/java/com/fishbook/identity/bootstrap/AdminBootstrapProperties.java`: validated `fishbook.admin.bootstrap` configuration.
- `backend/src/main/java/com/fishbook/identity/bootstrap/AdminBootstrapConflictException.java`: safe startup failure for a user-owned email.
- `backend/src/main/java/com/fishbook/identity/bootstrap/AdminBootstrapService.java`: idempotent transactional account creation.
- `backend/src/main/java/com/fishbook/identity/bootstrap/AdminBootstrapConfiguration.java`: properties registration and startup runner.
- `backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java`: administrator matcher and stable auth failures.
- `backend/src/main/resources/application.yml`: disabled bootstrap defaults.
- `backend/src/main/resources/application-local.yml`: environment bindings for local/deployed startup.

### Administration backend

- `backend/src/main/java/com/fishbook/administration/application/AdminFishQuery.java`: normalized keyword/status/page input.
- `backend/src/main/java/com/fishbook/administration/application/InvalidAdminFishQueryException.java`: stable invalid-query error.
- `backend/src/main/java/com/fishbook/administration/application/FishContentCommand.java`: application-level complete editable content.
- `backend/src/main/java/com/fishbook/administration/application/CreateFishCommand.java`: create-only slug plus content.
- `backend/src/main/java/com/fishbook/administration/application/AdminFishSummaryView.java`: management list row.
- `backend/src/main/java/com/fishbook/administration/application/AdminFishPageView.java`: fixed-size page.
- `backend/src/main/java/com/fishbook/administration/application/AdminFishDetailView.java`: complete management representation.
- `backend/src/main/java/com/fishbook/administration/application/FishAdministrationService.java`: management use-case interface.
- `backend/src/main/java/com/fishbook/administration/application/DefaultFishAdministrationService.java`: transactional orchestration.
- `backend/src/main/java/com/fishbook/administration/web/AdminFishController.java`: six management endpoints.
- `backend/src/main/java/com/fishbook/administration/web/dto/AdminFishCreateRequest.java`: validated flat create body.
- `backend/src/main/java/com/fishbook/administration/web/dto/AdminFishUpdateRequest.java`: validated flat update body without slug.
- `backend/src/main/java/com/fishbook/administration/web/dto/AdminFishSummaryResponse.java`: list-row JSON.
- `backend/src/main/java/com/fishbook/administration/web/dto/AdminFishPageResponse.java`: page JSON.
- `backend/src/main/java/com/fishbook/administration/web/dto/AdminFishDetailResponse.java`: detail/action JSON.
- `backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java`: management validation, conflict, and transition mapping.

### Administration frontend

- `frontend/src/features/auth/components/AdminRoute.tsx`: role-aware route guard.
- `frontend/src/features/auth/components/AdminRoute.test.tsx`: loading, anonymous, user, admin, and fault behavior.
- `frontend/src/features/auth/components/SessionNav.tsx`: admin-only management entry.
- `frontend/src/features/auth/api/sessionCache.ts`: clears administrator queries on logout/session expiry.
- `frontend/src/features/administration/model/types.ts`: status, page, detail, and request types.
- `frontend/src/features/administration/model/adminFishSearchParams.ts`: URL query normalization.
- `frontend/src/features/administration/model/adminFishSearchParams.test.ts`: URL contract.
- `frontend/src/features/administration/model/adminFishForm.ts`: Zod schema and DTO transformation.
- `frontend/src/features/administration/model/adminFishForm.test.ts`: normalization and boundaries.
- `frontend/src/features/administration/api/adminFishApi.ts`: typed management API and query keys.
- `frontend/src/features/administration/api/adminFishApi.test.ts`: request contract.
- `frontend/src/features/administration/components/AdminFishForm.tsx`: shared create/edit form.
- `frontend/src/features/administration/components/AdminFishForm.test.tsx`: client/server validation and pending state.
- `frontend/src/features/administration/pages/AdminFishListPage.tsx`: URL-driven management list.
- `frontend/src/features/administration/pages/AdminFishListPage.test.tsx`: filtering, pagination, empty/error/session states.
- `frontend/src/features/administration/pages/AdminFishNewPage.tsx`: draft creation flow.
- `frontend/src/features/administration/pages/AdminFishNewPage.test.tsx`: creation and navigation.
- `frontend/src/features/administration/pages/AdminFishEditPage.tsx`: edit, publish, unpublish, and republish flow.
- `frontend/src/features/administration/pages/AdminFishEditPage.test.tsx`: content and status actions.
- `frontend/src/features/administration/pages/AdminFishPages.module.css`: responsive list and form presentation.
- `frontend/src/app/router.tsx`: three guarded administrator routes.
- `frontend/src/features/catalog/api/catalogApi.ts`: exports a public-catalog root query key for invalidation.

### Release proof and documentation

- `.env.example`: local-only administrator bootstrap placeholders.
- `compose.full.yaml`: passes explicit bootstrap environment into the backend.
- `e2e/tests/admin-fish-flow.spec.ts`: real administrator lifecycle and ordinary-user denial.
- `README.md`: delivered capability, access route, configuration, and roadmap.
- `docs/runbooks/local-development.md`: safe bootstrap and smoke procedure.

---

### Task 1: Add Publication State to the Catalog Domain and Schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__add_fish_publication_status.sql`
- Create: `backend/src/main/java/com/fishbook/catalog/domain/PublicationStatus.java`
- Create: `backend/src/main/java/com/fishbook/catalog/domain/InvalidFishSpeciesException.java`
- Create: `backend/src/main/java/com/fishbook/catalog/domain/FishSpeciesContent.java`
- Create: `backend/src/main/java/com/fishbook/catalog/domain/InvalidPublicationTransitionException.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/domain/FishSpecies.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/domain/ImageAttribution.java`
- Test: `backend/src/test/java/com/fishbook/catalog/domain/FishSpeciesTest.java`
- Test: `backend/src/test/java/com/fishbook/catalog/persistence/CatalogDatabaseMigrationTest.java`

**Interfaces:**
- Consumes: existing V3–V8 MySQL schema and the existing `ImageAttribution`/`HabitatType` value types.
- Produces: `PublicationStatus`, `FishSpeciesContent`, `FishSpecies.createDraft`, `FishSpecies.restore`, `FishSpecies.edit`, `FishSpecies.publish`, and `FishSpecies.unpublish` for every later backend task.

- [ ] **Step 1: Write the failing V9 migration assertions**

Extend `CatalogDatabaseMigrationTest` with assertions that inspect the two columns and their migrated values:

```java
@Test
void addsAndBackfillsPublicationState() {
    Map<String, Object> columns = jdbcTemplate.queryForMap("""
            SELECT
                MAX(CASE WHEN column_name = 'publication_status' THEN is_nullable END) status_nullable,
                MAX(CASE WHEN column_name = 'published_at' THEN is_nullable END) published_nullable
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'fish_species'
              AND column_name IN ('publication_status', 'published_at')
            """);

    assertThat(columns.get("status_nullable")).isEqualTo("NO");
    assertThat(columns.get("published_nullable")).isEqualTo("YES");
    assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM fish_species WHERE publication_status <> 'PUBLISHED'",
            Integer.class)).isZero();
    assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM fish_species WHERE published_at IS NULL",
            Integer.class)).isZero();
}
```

Also assert that `ck_fish_species_publication_status` and `ix_fish_species_publication_display_order` exist.

- [ ] **Step 2: Run the migration test and verify RED**

Run: `cd backend && ./mvnw -Dtest=CatalogDatabaseMigrationTest test`

Expected: FAIL because `publication_status` and `published_at` do not exist.

- [ ] **Step 3: Add the V9 migration**

Create the migration with the exact shape below:

```sql
ALTER TABLE fish_species
    ADD COLUMN publication_status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' AFTER display_order,
    ADD COLUMN published_at TIMESTAMP(6) NULL AFTER publication_status,
    ADD CONSTRAINT ck_fish_species_publication_status CHECK (
        publication_status IN ('DRAFT', 'PUBLISHED', 'UNPUBLISHED')
    );

UPDATE fish_species
SET published_at = created_at
WHERE publication_status = 'PUBLISHED' AND published_at IS NULL;

ALTER TABLE fish_species
    ALTER COLUMN publication_status DROP DEFAULT;

CREATE INDEX ix_fish_species_publication_display_order
    ON fish_species (publication_status, display_order, id);
```

The temporary default exists only so existing rows can be altered safely; dropping it ensures every future insert chooses a status explicitly.

- [ ] **Step 4: Run the migration test and verify GREEN**

Run: `cd backend && ./mvnw -Dtest=CatalogDatabaseMigrationTest test`

Expected: PASS with all 12 existing fish reported as `PUBLISHED` and with non-null `published_at`.

- [ ] **Step 5: Write failing publication-domain tests**

Add focused tests to `FishSpeciesTest`:

```java
private static final Instant CREATED = Instant.parse("2026-08-30T01:00:00Z");
private static final Instant LATER = Instant.parse("2026-08-30T02:00:00Z");

@Test
void createsACompleteDraftWithoutPublishingIt() {
    FishSpecies fish = FishSpecies.createDraft("test-fish", validContent(), CREATED);

    assertThat(fish.id()).isNull();
    assertThat(fish.status()).isEqualTo(PublicationStatus.DRAFT);
    assertThat(fish.publishedAt()).isNull();
    assertThat(fish.createdAt()).isEqualTo(CREATED);
    assertThat(fish.updatedAt()).isEqualTo(CREATED);
}

@Test
void editsContentWithoutChangingIdentityOrPublicationState() {
    FishSpecies published = FishSpecies.restore(
            7L, "test-fish", validContent(), PublicationStatus.PUBLISHED,
            CREATED, CREATED, CREATED);

    FishSpecies edited = published.edit(contentNamed("测试鱼二号"), LATER);

    assertThat(edited.id()).isEqualTo(7L);
    assertThat(edited.slug()).isEqualTo("test-fish");
    assertThat(edited.status()).isEqualTo(PublicationStatus.PUBLISHED);
    assertThat(edited.publishedAt()).isEqualTo(CREATED);
    assertThat(edited.commonNameZh()).isEqualTo("测试鱼二号");
    assertThat(edited.updatedAt()).isEqualTo(LATER);
}

@Test
void allowsOnlyTheThreeApprovedPublicationTransitions() {
    FishSpecies draft = FishSpecies.createDraft("test-fish", validContent(), CREATED);
    FishSpecies published = draft.publish(LATER);
    FishSpecies unpublished = published.unpublish(LATER.plusSeconds(60));
    FishSpecies republished = unpublished.publish(LATER.plusSeconds(120));

    assertThat(published.status()).isEqualTo(PublicationStatus.PUBLISHED);
    assertThat(unpublished.status()).isEqualTo(PublicationStatus.UNPUBLISHED);
    assertThat(unpublished.publishedAt()).isEqualTo(LATER);
    assertThat(republished.status()).isEqualTo(PublicationStatus.PUBLISHED);
    assertThat(republished.publishedAt()).isEqualTo(LATER.plusSeconds(120));

    assertThatThrownBy(() -> draft.unpublish(LATER))
            .isInstanceOf(InvalidPublicationTransitionException.class);
    assertThatThrownBy(() -> published.publish(LATER.plusSeconds(60)))
            .isInstanceOf(InvalidPublicationTransitionException.class);
    assertThatThrownBy(() -> unpublished.unpublish(LATER.plusSeconds(120)))
            .isInstanceOf(InvalidPublicationTransitionException.class);
}
```

Add boundary tests for canonical/120-character slug, unique nonblank aliases of at most 100 code points, at least one habitat, schema-sized VARCHAR fields, 10,000-code-point content fields, local image path, positive display order, non-null audit/status values, and defensive copies.

- [ ] **Step 6: Run the domain test and verify RED**

Run: `cd backend && ./mvnw -Dtest=FishSpeciesTest test`

Expected: compilation failure because publication types and aggregate methods do not exist.

- [ ] **Step 7: Implement the minimal pure domain model**

Use these public shapes:

```java
public enum PublicationStatus {
    DRAFT,
    PUBLISHED,
    UNPUBLISHED
}

public final class InvalidFishSpeciesException extends IllegalArgumentException {
    public InvalidFishSpeciesException(String message) {
        super(message);
    }

    public String code() {
        return "VALIDATION_FAILED";
    }
}

public final class InvalidPublicationTransitionException extends RuntimeException {
    public InvalidPublicationTransitionException(PublicationStatus from, PublicationStatus to) {
        super("Cannot change fish publication status from " + from + " to " + to);
    }

    public String code() {
        return "INVALID_PUBLICATION_TRANSITION";
    }
}

public record FishSpeciesContent(
        String commonNameZh,
        String scientificName,
        String familyNameZh,
        String familyScientificName,
        String genusNameZh,
        String genusScientificName,
        List<String> aliases,
        Set<HabitatType> habitats,
        String appearance,
        String sizeDescription,
        String habitatDescription,
        String distribution,
        String description,
        ImageAttribution image,
        int displayOrder) {

    public FishSpeciesContent {
        commonNameZh = required(commonNameZh, 100, "commonNameZh");
        scientificName = required(scientificName, 160, "scientificName");
        familyNameZh = required(familyNameZh, 100, "familyNameZh");
        familyScientificName = required(familyScientificName, 160, "familyScientificName");
        genusNameZh = required(genusNameZh, 100, "genusNameZh");
        genusScientificName = required(genusScientificName, 160, "genusScientificName");
        appearance = required(appearance, 10_000, "appearance");
        sizeDescription = required(sizeDescription, 10_000, "sizeDescription");
        habitatDescription = required(habitatDescription, 10_000, "habitatDescription");
        distribution = required(distribution, 10_000, "distribution");
        description = required(description, 10_000, "description");
        aliases = normalizedAliases(aliases);
        habitats = immutableHabitats(habitats);
        if (image == null) throw new InvalidFishSpeciesException("image must not be null");
        if (displayOrder <= 0) {
            throw new InvalidFishSpeciesException("displayOrder must be positive");
        }
    }

    private static String required(String value, int maxCodePoints, String field) {
        if (value == null) throw new InvalidFishSpeciesException(field + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty()
                || normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw new InvalidFishSpeciesException(field + " has an invalid length");
        }
        return normalized;
    }

    private static List<String> normalizedAliases(List<String> values) {
        if (values == null) throw new InvalidFishSpeciesException("aliases must not be null");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String alias = required(value, 100, "alias");
            if (!normalized.add(alias)) {
                throw new InvalidFishSpeciesException("aliases must be unique after normalization");
            }
        }
        return List.copyOf(normalized);
    }

    private static Set<HabitatType> immutableHabitats(Set<HabitatType> values) {
        if (values == null) throw new InvalidFishSpeciesException("habitats must not be null");
        if (values.isEmpty() || values.contains(null)) {
            throw new InvalidFishSpeciesException("habitats must contain at least one value");
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
```

Refactor `FishSpecies` to store `FishSpeciesContent content` and provide compatibility accessors such as `commonNameZh()`, `aliases()`, `habitats()`, and `image()` so existing application mapping stays readable. `publish` and `unpublish` each take an `Instant now`. Make `restore` require `publishedAt == null` for `DRAFT` and `publishedAt != null` for both `PUBLISHED` and `UNPUBLISHED`. Update `ImageAttribution` to strip its fields, enforce the schema limits `(255, 255, 1000, 255, 100, 1000)`, retain the local image-path regex, and accept only absolute `http` or `https` source/license URLs. Every validation failure reachable from administrator content must throw `InvalidFishSpeciesException`; constructor misuse involving persisted IDs or timestamps may remain a plain `IllegalArgumentException`.

- [ ] **Step 8: Run focused and catalog-domain tests and verify GREEN**

Run:

```bash
cd backend
./mvnw -Dtest=FishSpeciesTest,CatalogDatabaseMigrationTest test
```

Expected: both test classes pass.

- [ ] **Step 9: Commit the publication domain slice**

```bash
git add backend/src/main/resources/db/migration/V9__add_fish_publication_status.sql \
  backend/src/main/java/com/fishbook/catalog/domain \
  backend/src/test/java/com/fishbook/catalog/domain/FishSpeciesTest.java \
  backend/src/test/java/com/fishbook/catalog/persistence/CatalogDatabaseMigrationTest.java
git commit -m "feat: model fish publication lifecycle"
```

---

### Task 2: Separate Public, Historical-Reference, and Management Persistence

**Files:**
- Create: `backend/src/main/java/com/fishbook/catalog/domain/FishManagementSearchCriteria.java`
- Create: `backend/src/main/java/com/fishbook/catalog/domain/FishManagementRepository.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/domain/FishRepository.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/persistence/FishSpeciesJpaEntity.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/persistence/FishAliasJpaEntity.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/persistence/FishHabitatId.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/persistence/FishHabitatJpaEntity.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/persistence/SpringDataFishSpeciesJpaRepository.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/persistence/JpaFishRepositoryAdapter.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/application/FishCatalogQueryService.java`
- Modify: `backend/src/main/java/com/fishbook/catalog/application/DefaultFishCatalogQueryService.java`
- Modify: `backend/src/main/java/com/fishbook/favorites/application/DefaultFavoriteApplicationService.java`
- Modify: `backend/src/main/java/com/fishbook/catchlog/application/DefaultCatchRecordApplicationService.java`
- Test: `backend/src/test/java/com/fishbook/catalog/persistence/JpaFishRepositoryAdapterTest.java`
- Test: `backend/src/test/java/com/fishbook/catalog/application/DefaultFishCatalogQueryServiceTest.java`
- Test: `backend/src/test/java/com/fishbook/favorites/application/DefaultFavoriteApplicationServiceTest.java`
- Test: `backend/src/test/java/com/fishbook/catchlog/application/DefaultCatchRecordApplicationServiceTest.java`

**Interfaces:**
- Consumes: publication-aware aggregate and V9 schema from Task 1.
- Produces: published-only catalog methods, `getReferenceBySlugIncludingUnpublished`, and `FishManagementRepository` methods used by Task 4.

- [ ] **Step 1: Write failing persistence visibility and write-round-trip tests**

Update the SQL fixture helper in `JpaFishRepositoryAdapterTest` to insert `publication_status` and `published_at`, then insert one fish in each status. Add these assertions:

```java
@Test
void publicQueriesReturnOnlyPublishedFish() {
    assertThat(adapter.searchPublished(new FishSearchCriteria(null, null, null, 0, 12)).items())
            .extracting(FishSpecies::status)
            .containsOnly(PublicationStatus.PUBLISHED);
    assertThat(adapter.findPublishedBySlug("draft-fish")).isEmpty();
    assertThat(adapter.findPublishedBySlug("unpublished-fish")).isEmpty();
    assertThat(adapter.findPublishedAvailableFamilies())
            .doesNotContain("草稿科", "下架科");
    assertThat(adapter.findPublishedAvailableHabitats())
            .doesNotContain(HabitatType.STREAM);
}

@Test
void internalReferencesCanStillReadUnpublishedFish() {
    assertThat(adapter.findAnyBySlug("unpublished-fish")).get()
            .extracting(FishSpecies::status)
            .isEqualTo(PublicationStatus.UNPUBLISHED);
    assertThat(adapter.findAllByIds(List.of(unpublishedId)))
            .extracting(FishSpecies::slug)
            .containsExactly("unpublished-fish");
}

@Test
void managementSearchFiltersStatusAndSortsByRecentUpdate() {
    FishPage page = adapter.searchManaged(new FishManagementSearchCriteria(
            null, PublicationStatus.DRAFT, 0, 20));

    assertThat(page.items()).extracting(FishSpecies::slug).containsExactly("draft-fish");
}

@Test
void savesNewAggregateAndReplacesAliasesAndHabitatsOnEdit() {
    FishSpecies saved = adapter.save(FishSpecies.createDraft("new-fish", validContent(), NOW));
    FishSpecies edited = adapter.save(saved.edit(editedContent(), LATER));

    assertThat(edited.id()).isPositive();
    assertThat(edited.aliases()).containsExactly("新别名");
    assertThat(edited.habitats()).containsExactly(HabitatType.LAKE);
    assertThat(adapter.findManagedById(edited.id())).contains(edited);
}
```

- [ ] **Step 2: Run the adapter test and verify RED**

Run: `cd backend && ./mvnw -Dtest=JpaFishRepositoryAdapterTest test`

Expected: compilation failure because the explicit public/management methods do not exist.

- [ ] **Step 3: Define the two repository ports**

Use these exact contracts:

```java
public interface FishRepository {
    FishPage searchPublished(FishSearchCriteria criteria);
    Optional<FishSpecies> findPublishedBySlug(String slug);
    Optional<FishSpecies> findAnyBySlug(String slug);
    List<FishSpecies> findAllByIds(List<Long> ids);
    List<FishSpecies> findAllPublishedBySlugs(List<String> slugs);
    List<String> findPublishedAvailableFamilies();
    List<HabitatType> findPublishedAvailableHabitats();
}

public record FishManagementSearchCriteria(
        String query,
        PublicationStatus status,
        int page,
        int size) {}

public interface FishManagementRepository {
    FishPage searchManaged(FishManagementSearchCriteria criteria);
    Optional<FishSpecies> findManagedById(long id);
    FishSpecies save(FishSpecies fish);
}
```

`FishRepository` expresses public selection and private-history needs; `FishManagementRepository` is the only write port.

- [ ] **Step 4: Implement publication-aware JPA queries and aggregate writes**

Make `JpaFishRepositoryAdapter` implement both ports. Add `publicationStatus` and `publishedAt` to `FishSpeciesJpaEntity`, and change both child collections to `cascade = CascadeType.ALL, orphanRemoval = true`. Add package-private constructors that attach aliases and habitats to their owning species.

Use a public ID-page query with a mandatory status predicate:

```java
and f.publicationStatus = com.fishbook.catalog.domain.PublicationStatus.PUBLISHED
```

Use a separate management ID-page query where status is optional and keyword matches `slug`, Chinese name, scientific name, or alias. Sort management pages by `updatedAt DESC, id DESC`. Query both available families and available habitat codes from published rows only, and restore habitat options in `HabitatType` declaration order.

Implement aggregate updates by loading the managed entity with aliases/habitats, calling an `apply(FishSpecies fish)` method that copies every mutable scalar plus publication fields, and replacing both child collections. Do not build a detached entity for updates, and never overwrite the stored slug when `fish.id()` is non-null.

- [ ] **Step 5: Run the adapter test and verify GREEN**

Run: `cd backend && ./mvnw -Dtest=JpaFishRepositoryAdapterTest test`

Expected: public filtering, internal lookup, management filtering, creation, and child replacement all pass against MySQL.

- [ ] **Step 6: Write failing application regressions for visibility and historical references**

Add tests proving these service-level semantics:

```java
@Test
void publicDetailAndNewAssociationUsePublishedLookup() {
    service.getBySlug("published-fish");
    service.getReferenceBySlug("published-fish");

    assertThat(repository.publishedSlugLookups()).containsExactly(
            "published-fish", "published-fish");
}

@Test
void historicalSummaryAndExplicitInternalReferenceCanReadUnpublishedFish() {
    assertThat(service.getSummariesByIds(List.of(9L))).hasSize(1);
    assertThat(service.getReferenceBySlugIncludingUnpublished("unpublished-fish").id())
            .isEqualTo(9L);
}
```

Also assert `getFilterOptions()` contains only the repository's published families and published habitats. In `DefaultFavoriteApplicationServiceTest`, assert `add` uses `getReferenceBySlug` while `remove` uses `getReferenceBySlugIncludingUnpublished`. In `DefaultCatchRecordApplicationServiceTest`, assert create and changing the fish on update use the published method, but an update that retains the existing unpublished fish uses the internal method.

- [ ] **Step 7: Run the service tests and verify RED**

Run:

```bash
cd backend
./mvnw -Dtest=DefaultFishCatalogQueryServiceTest,DefaultFavoriteApplicationServiceTest,DefaultCatchRecordApplicationServiceTest test
```

Expected: compilation or assertion failures because the service still uses generic lookup semantics.

- [ ] **Step 8: Implement explicit service lookup semantics**

Add this method to `FishCatalogQueryService`:

```java
FishReferenceView getReferenceBySlugIncludingUnpublished(String slug);
```

Keep `getReferenceBySlug` and `getReferencesBySlugs` published-only. Keep `getSummariesByIds` all-status so private histories resolve. Build `getFilterOptions` from `findPublishedAvailableFamilies()` and `findPublishedAvailableHabitats()` instead of all enum values. Change favorite removal to the new internal reference method.

For catch updates, resolve the existing fish summary first:

```java
FishSummaryView existingFish = summaryFor(existing.details().fishId());
FishReferenceView selectedFish = command.fishSlug().equals(existingFish.slug())
        ? fishCatalogQueryService.getReferenceBySlugIncludingUnpublished(command.fishSlug())
        : fishCatalogQueryService.getReferenceBySlug(command.fishSlug());
```

This permits editing notes or measurements without forcing an unpublished historical fish to change, while preventing a new unpublished association.

- [ ] **Step 9: Run focused services plus catalog API regression and verify GREEN**

Run:

```bash
cd backend
./mvnw -Dtest=DefaultFishCatalogQueryServiceTest,DefaultFavoriteApplicationServiceTest,DefaultCatchRecordApplicationServiceTest,FishCatalogApiIntegrationTest test
```

Expected: all selected tests pass and seeded public behavior is unchanged.

- [ ] **Step 10: Commit the persistence and visibility slice**

```bash
git add backend/src/main/java/com/fishbook/catalog \
  backend/src/main/java/com/fishbook/favorites/application/DefaultFavoriteApplicationService.java \
  backend/src/main/java/com/fishbook/catchlog/application/DefaultCatchRecordApplicationService.java \
  backend/src/test/java/com/fishbook/catalog \
  backend/src/test/java/com/fishbook/favorites/application/DefaultFavoriteApplicationServiceTest.java \
  backend/src/test/java/com/fishbook/catchlog/application/DefaultCatchRecordApplicationServiceTest.java
git commit -m "feat: enforce fish publication visibility"
```

---

### Task 3: Bootstrap Administrators and Enforce the Server-Side Role Boundary

**Files:**
- Create: `backend/src/main/java/com/fishbook/common/time/ApplicationClockConfiguration.java`
- Delete: `backend/src/main/java/com/fishbook/catchlog/application/CatchLogClockConfiguration.java`
- Create: `backend/src/main/java/com/fishbook/identity/domain/IdentityInputValidator.java`
- Create: `backend/src/main/java/com/fishbook/identity/bootstrap/AdminBootstrapProperties.java`
- Create: `backend/src/main/java/com/fishbook/identity/bootstrap/AdminBootstrapConflictException.java`
- Create: `backend/src/main/java/com/fishbook/identity/bootstrap/AdminBootstrapService.java`
- Create: `backend/src/main/java/com/fishbook/identity/bootstrap/AdminBootstrapConfiguration.java`
- Modify: `backend/src/main/java/com/fishbook/identity/domain/User.java`
- Modify: `backend/src/main/java/com/fishbook/identity/application/DefaultAuthApplicationService.java`
- Modify: `backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-local.yml`
- Test: `backend/src/test/java/com/fishbook/identity/bootstrap/AdminBootstrapConfigurationTest.java`
- Test: `backend/src/test/java/com/fishbook/identity/bootstrap/AdminBootstrapServiceTest.java`
- Test: `backend/src/test/java/com/fishbook/identity/application/DefaultAuthApplicationServiceTest.java`
- Test: `backend/src/test/java/com/fishbook/identity/web/AdminAuthorizationTest.java`

**Interfaces:**
- Consumes: existing `UserRepository`, `PasswordHasher`, `UserRole`, and Spring Security session configuration.
- Produces: an idempotent bootstrapped `ADMIN` and a `/api/v1/admin/**` boundary used by Task 4.

- [ ] **Step 1: Write failing shared-validation and bootstrap service tests**

Keep every existing registration validation test, then add bootstrap tests using fakes:

```java
@Test
void createsAnActiveAdministratorWithNormalizedEmailAndHashedPassword() {
    service.bootstrap(new AdminBootstrapProperties(
            true, " Admin@Example.COM ", "strong-admin-pass", "管理员"));

    User saved = repository.savedUser();
    assertThat(saved.email()).isEqualTo("admin@example.com");
    assertThat(saved.role()).isEqualTo(UserRole.ADMIN);
    assertThat(saved.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(saved.passwordHash()).isEqualTo("hashed:strong-admin-pass");
}

@Test
void existingAdministratorMakesBootstrapANoOp() {
    repository.put(existingUser(UserRole.ADMIN));

    service.bootstrap(validProperties());

    assertThat(repository.saveCount()).isZero();
    assertThat(passwordHasher.hashCount()).isZero();
}

@Test
void refusesToPromoteAnExistingUser() {
    repository.put(existingUser(UserRole.USER));

    assertThatThrownBy(() -> service.bootstrap(validProperties()))
            .isInstanceOf(AdminBootstrapConflictException.class)
            .hasMessageNotContaining("strong-admin-pass");
    assertThat(repository.saveCount()).isZero();
}
```

Also assert email/password/nickname boundaries exactly match registration.

- [ ] **Step 2: Run identity tests and verify RED**

Run:

```bash
cd backend
./mvnw -Dtest=DefaultAuthApplicationServiceTest,AdminBootstrapServiceTest test
```

Expected: compilation failure because bootstrap and shared validator types do not exist.

- [ ] **Step 3: Extract identity input validation and implement bootstrap service**

Move the existing `Clock` bean from the catch-specific package into `com.fishbook.common.time.ApplicationClockConfiguration` without changing its `Asia/Shanghai` zone; keep exactly one `Clock` bean in the application context. Then create static methods with exact signatures:

```java
public final class IdentityInputValidator {
    public static String normalizeAndValidateEmail(String email);
    public static String validatePassword(String password);
    public static String validateNickname(String nickname);
}
```

Move the existing registration rules without changing them. Make `DefaultAuthApplicationService` call the shared methods. Add:

```java
public static User initializeAdmin(
        String normalizedEmail,
        String passwordHash,
        String nickname,
        Instant now) {
    return new User(
            null, normalizedEmail, passwordHash, nickname,
            UserRole.ADMIN, UserStatus.ACTIVE, now, now);
}
```

`AdminBootstrapService.bootstrap` returns immediately when disabled, validates before repository access, no-ops for an existing admin, rejects an existing user, and hashes only when creating a new account. Mark it transactional and inject the existing `Clock`.

- [ ] **Step 4: Run identity tests and verify GREEN**

Run:

```bash
cd backend
./mvnw -Dtest=DefaultAuthApplicationServiceTest,AdminBootstrapServiceTest test
```

Expected: registration remains green and all bootstrap service cases pass.

- [ ] **Step 5: Write failing configuration validation tests**

Use `ApplicationContextRunner` as in `MediaConfigurationTest`:

```java
private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(AdminBootstrapConfiguration.class)
        .withBean(UserRepository.class, FakeUserRepository::new)
        .withBean(PasswordHasher.class, FakePasswordHasher::new)
        .withBean(Clock.class, Clock::systemUTC);

@Test
void disabledBootstrapAcceptsMissingCredentials() {
    runner.withPropertyValues("fishbook.admin.bootstrap.enabled=false")
            .run(context -> assertThat(context).hasNotFailed());
}

@Test
void enabledBootstrapRejectsBlankConfiguration() {
    runner.withPropertyValues(
            "fishbook.admin.bootstrap.enabled=true",
            "fishbook.admin.bootstrap.email=",
            "fishbook.admin.bootstrap.password=strong-admin-pass",
            "fishbook.admin.bootstrap.nickname=管理员")
            .run(context -> assertThat(context).hasFailed());
}
```

Test each missing field and a complete enabled configuration.

- [ ] **Step 6: Implement properties, startup runner, and environment binding**

Use:

```java
@Validated
@ConfigurationProperties("fishbook.admin.bootstrap")
public record AdminBootstrapProperties(
        boolean enabled,
        String email,
        String password,
        String nickname) {

    @AssertTrue(message = "email, password and nickname are required when admin bootstrap is enabled")
    public boolean isValidWhenEnabled() {
        return !enabled || Stream.of(email, password, nickname)
                .allMatch(value -> value != null && !value.isBlank());
    }
}
```

`AdminBootstrapConfiguration` enables the properties, constructs `AdminBootstrapService` from `UserRepository`, `PasswordHasher`, and `Clock`, and exposes an `ApplicationRunner` that calls the service once. Keep `AdminBootstrapService` free of component annotations so `ApplicationContextRunner` and the production application use the same explicit beans. In `application.yml`, default `enabled` to `false`; in `application-local.yml`, bind all four values to `FISHBOOK_ADMIN_*` variables with empty defaults for secret-bearing strings.

- [ ] **Step 7: Run configuration tests and verify GREEN**

Run: `cd backend && ./mvnw -Dtest=AdminBootstrapConfigurationTest test`

Expected: disabled, enabled-valid, and enabled-invalid cases behave as specified without logging the password.

- [ ] **Step 8: Write failing administrator authorization tests**

Create `AdminAuthorizationTest` and request `/api/v1/admin/fishes` before the controller exists:

```java
@Test
void anonymousAdminReadRequiresAuthentication() throws Exception {
    mvc.perform(get("/api/v1/admin/fishes"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
}

@Test
@WithMockUser(roles = "USER")
void ordinaryUserCannotReadAdministration() throws Exception {
    mvc.perform(get("/api/v1/admin/fishes"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
}

@Test
@WithMockUser(roles = "ADMIN")
void adminPassesTheRoleBoundary() throws Exception {
    mvc.perform(get("/api/v1/admin/fishes"))
            .andExpect(status().isNotFound());
}

@Test
@WithMockUser(roles = "ADMIN")
void adminWriteWithoutCsrfIsRejectedBeforeRouting() throws Exception {
    mvc.perform(post("/api/v1/admin/fishes").contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
}
```

- [ ] **Step 9: Run authorization tests and verify RED**

Run: `cd backend && ./mvnw -Dtest=AdminAuthorizationTest test`

Expected: anonymous requests currently return `403 ACCESS_DENIED` because the admin matcher is absent.

- [ ] **Step 10: Add the Spring Security administrator matcher**

Add `/api/v1/admin/**` to `authenticationRequiredEndpoints`, then place this matcher before the general authenticated and deny-all rules:

```java
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
```

Keep public catalog GET matchers unchanged. Ensure anonymous CSRF failures under `/api/v1/admin/**` map to `401`, while authenticated missing-CSRF failures remain `403 CSRF_INVALID`.

- [ ] **Step 11: Run security and identity regression tests and verify GREEN**

Run:

```bash
cd backend
./mvnw -Dtest=AdminAuthorizationTest,IdentityAuthorizationTest,AuthFlowIntegrationTest,DefaultAuthApplicationServiceTest,AdminBootstrapServiceTest,AdminBootstrapConfigurationTest test
```

Expected: all selected tests pass.

- [ ] **Step 12: Commit the bootstrap and security slice**

```bash
git add backend/src/main/java/com/fishbook/identity \
  backend/src/main/java/com/fishbook/common/time/ApplicationClockConfiguration.java \
  backend/src/main/java/com/fishbook/catchlog/application/CatchLogClockConfiguration.java \
  backend/src/main/resources/application.yml \
  backend/src/main/resources/application-local.yml \
  backend/src/test/java/com/fishbook/identity
git commit -m "feat: bootstrap and authorize administrators"
```

---

### Task 4: Expose the Complete Administration API

**Files:**
- Create: `backend/src/main/java/com/fishbook/administration/application/AdminFishQuery.java`
- Create: `backend/src/main/java/com/fishbook/administration/application/InvalidAdminFishQueryException.java`
- Create: `backend/src/main/java/com/fishbook/administration/application/FishContentCommand.java`
- Create: `backend/src/main/java/com/fishbook/administration/application/CreateFishCommand.java`
- Create: `backend/src/main/java/com/fishbook/administration/application/AdminFishSummaryView.java`
- Create: `backend/src/main/java/com/fishbook/administration/application/AdminFishPageView.java`
- Create: `backend/src/main/java/com/fishbook/administration/application/AdminFishDetailView.java`
- Create: `backend/src/main/java/com/fishbook/administration/application/FishAdministrationService.java`
- Create: `backend/src/main/java/com/fishbook/administration/application/DefaultFishAdministrationService.java`
- Create: `backend/src/main/java/com/fishbook/administration/web/AdminFishController.java`
- Create: `backend/src/main/java/com/fishbook/administration/web/dto/AdminFishCreateRequest.java`
- Create: `backend/src/main/java/com/fishbook/administration/web/dto/AdminFishUpdateRequest.java`
- Create: `backend/src/main/java/com/fishbook/administration/web/dto/AdminFishSummaryResponse.java`
- Create: `backend/src/main/java/com/fishbook/administration/web/dto/AdminFishPageResponse.java`
- Create: `backend/src/main/java/com/fishbook/administration/web/dto/AdminFishDetailResponse.java`
- Modify: `backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/fishbook/administration/application/DefaultFishAdministrationServiceTest.java`
- Test: `backend/src/test/java/com/fishbook/administration/web/AdminFishApiIntegrationTest.java`
- Modify test: `backend/src/test/java/com/fishbook/identity/web/AdminAuthorizationTest.java`

**Interfaces:**
- Consumes: `FishManagementRepository`, publication aggregate, admin security boundary, and shared `Clock` from Tasks 1–3.
- Produces: the exact six `/api/v1/admin/fishes` endpoints consumed by the frontend in Tasks 6–7.

- [ ] **Step 1: Write failing query and application-service tests**

Specify raw query normalization and all use cases:

```java
@Test
void normalizesManagementQueryAndRejectsClientPageSize() {
    assertThat(AdminFishQuery.from("  鲤  ", "draft", "2", null))
            .isEqualTo(new AdminFishQuery("鲤", PublicationStatus.DRAFT, 2));
    assertThatThrownBy(() -> AdminFishQuery.from(null, null, "0", "20"))
            .isInstanceOf(InvalidAdminFishQueryException.class);
}

@Test
void createsDraftAndKeepsSlugImmutableDuringEdit() {
    AdminFishDetailView created = service.create(createCommand("new-fish"));
    AdminFishDetailView edited = service.update(created.id(), editedContentCommand());

    assertThat(created.slug()).isEqualTo("new-fish");
    assertThat(created.status()).isEqualTo(PublicationStatus.DRAFT);
    assertThat(edited.slug()).isEqualTo("new-fish");
    assertThat(edited.commonNameZh()).isEqualTo("新名称");
    assertThat(edited.status()).isEqualTo(PublicationStatus.DRAFT);
}

@Test
void publishesUnpublishesAndRepublishesThroughExplicitActions() {
    long id = repository.save(FishSpecies.createDraft("new-fish", content(), NOW)).id();

    assertThat(service.publish(id).status()).isEqualTo(PublicationStatus.PUBLISHED);
    assertThat(service.unpublish(id).status()).isEqualTo(PublicationStatus.UNPUBLISHED);
    assertThat(service.publish(id).status()).isEqualTo(PublicationStatus.PUBLISHED);
}
```

Also cover list status filtering, missing ID, validation propagation, and invalid transitions.

- [ ] **Step 2: Run application tests and verify RED**

Run: `cd backend && ./mvnw -Dtest=DefaultFishAdministrationServiceTest test`

Expected: compilation failure because the administration application package does not exist.

- [ ] **Step 3: Implement application contracts and transactional service**

Use this service interface:

```java
public record FishContentCommand(
        String commonNameZh,
        String scientificName,
        String familyNameZh,
        String familyScientificName,
        String genusNameZh,
        String genusScientificName,
        List<String> aliases,
        Set<HabitatType> habitats,
        String appearance,
        String sizeDescription,
        String habitatDescription,
        String distribution,
        String description,
        String imagePath,
        String imageAltText,
        String imageSourceUrl,
        String imageAuthor,
        String imageLicenseName,
        String imageLicenseUrl,
        int displayOrder) {

    FishSpeciesContent toDomain() {
        return new FishSpeciesContent(
                commonNameZh, scientificName, familyNameZh, familyScientificName,
                genusNameZh, genusScientificName, aliases, habitats, appearance,
                sizeDescription, habitatDescription, distribution, description,
                new ImageAttribution(
                        imagePath, imageAltText, imageSourceUrl, imageAuthor,
                        imageLicenseName, imageLicenseUrl),
                displayOrder);
    }
}

public record CreateFishCommand(String slug, FishContentCommand content) {}

public interface FishAdministrationService {
    AdminFishPageView search(AdminFishQuery query);
    AdminFishDetailView get(long id);
    AdminFishDetailView create(CreateFishCommand command);
    AdminFishDetailView update(long id, FishContentCommand command);
    AdminFishDetailView publish(long id);
    AdminFishDetailView unpublish(long id);
}
```

`AdminFishSummaryView` is `(id, slug, commonNameZh, scientificName, status, updatedAt)`. `AdminFishPageView` is `(items, page, size, totalItems, totalPages)` and defensively copies `items`. `AdminFishDetailView` is a flat record containing `id`, `slug`, every `FishContentCommand` field except the image fields retain their `image*` prefixes, `status`, nullable `publishedAt`, `createdAt`, and `updatedAt`; it defensively copies aliases and habitats. These names are the JSON names in `AdminFishDetailResponse` and the TypeScript names in Task 6.

`AdminFishQuery.from(rawQuery, rawStatus, rawPage, rawSize)` accepts an optional keyword of at most 100 code points, a case-insensitive enum status, a non-negative page, and no `size`. `DefaultFishAdministrationService` uses page size `20`, marks reads `@Transactional(readOnly = true)`, marks each write `@Transactional`, loads missing IDs through one helper that throws `FishNotFoundException(String.valueOf(id))`, and maps domain objects to immutable views.

- [ ] **Step 4: Run application tests and verify GREEN**

Run: `cd backend && ./mvnw -Dtest=DefaultFishAdministrationServiceTest test`

Expected: all management use cases pass against a fake `FishManagementRepository`.

- [ ] **Step 5: Write failing API and exact authorization tests**

Create `AdminFishApiIntegrationTest` with real MySQL, `@WithMockUser(roles = "ADMIN")`, and `.with(csrf())` on writes. Cover:

```java
@Test
@WithMockUser(roles = "ADMIN")
void createsEditsPublishesAndUnpublishesFish() throws Exception {
    MvcResult created = mvc.perform(post("/api/v1/admin/fishes")
                    .with(csrf())
                    .contentType(APPLICATION_JSON)
                    .content(validCreateJson("api-test-fish")))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("/api/v1/admin/fishes/\\d+")))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn();

    Number parsedId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    long id = parsedId.longValue();
    mvc.perform(put("/api/v1/admin/fishes/{id}", id)
                    .with(csrf()).contentType(APPLICATION_JSON)
                    .content(validUpdateJson("更新名称")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("api-test-fish"))
            .andExpect(jsonPath("$.commonNameZh").value("更新名称"))
            .andExpect(jsonPath("$.status").value("DRAFT"));

    mvc.perform(post("/api/v1/admin/fishes/{id}/publish", id).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"));
    mvc.perform(post("/api/v1/admin/fishes/{id}/unpublish", id).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UNPUBLISHED"));
}
```

Also cover list keyword/status/page, detail, fixed-size rejection, invalid ID, every representative field error, a domain-only invalid case such as duplicate normalized aliases returning safe `400 VALIDATION_FAILED`, duplicate slug/name/scientific name, missing fish, illegal transition, anonymous `401`, user `403`, and admin missing-CSRF `403 CSRF_INVALID`. Replace the Task 3 admin-pass `404` assertion with a successful empty or seeded management list response.

- [ ] **Step 6: Run API tests and verify RED**

Run:

```bash
cd backend
./mvnw -Dtest=AdminFishApiIntegrationTest,AdminAuthorizationTest test
```

Expected: `404` or compilation failure because the controller and DTOs do not exist.

- [ ] **Step 7: Implement validated flat request DTOs and controller**

Both request records expose field names that match frontend form names. Apply `@NotBlank`, `@Size`, `@Pattern`, `@NotEmpty`, and `@Positive` using the exact domain/storage bounds. `AdminFishCreateRequest` includes `slug`; `AdminFishUpdateRequest` does not. Convert habitats to a `LinkedHashSet` and aliases to a list before constructing `FishContentCommand`.

Use this controller shape:

```java
@RestController
@RequestMapping("/api/v1/admin/fishes")
public final class AdminFishController {
    @GetMapping
    AdminFishPageResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size);

    @GetMapping("/{id}")
    AdminFishDetailResponse get(@PathVariable String id);

    @PostMapping
    ResponseEntity<AdminFishDetailResponse> create(@Valid @RequestBody AdminFishCreateRequest request);

    @PutMapping("/{id}")
    AdminFishDetailResponse update(
            @PathVariable String id,
            @Valid @RequestBody AdminFishUpdateRequest request);

    @PostMapping("/{id}/publish")
    AdminFishDetailResponse publish(@PathVariable String id);

    @PostMapping("/{id}/unpublish")
    AdminFishDetailResponse unpublish(@PathVariable String id);
}
```

Parse IDs as positive `long` values and map invalid values through `InvalidAdminFishQueryException`.

- [ ] **Step 8: Map management errors without leaking persistence details**

Extend `GlobalExceptionHandler` with:

```java
@ExceptionHandler(InvalidAdminFishQueryException.class)
ResponseEntity<ApiErrorResponse> handleInvalidAdminFishQuery(
        InvalidAdminFishQueryException exception, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_CATALOG_QUERY",
            "Catalog query is invalid", List.of(), request);
}

@ExceptionHandler(InvalidPublicationTransitionException.class)
ResponseEntity<ApiErrorResponse> handleInvalidPublicationTransition(
        InvalidPublicationTransitionException exception, HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, exception.code(),
            "Fish publication transition is invalid", List.of(), request);
}

@ExceptionHandler(InvalidFishSpeciesException.class)
ResponseEntity<ApiErrorResponse> handleInvalidFishSpecies(
        InvalidFishSpeciesException exception, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, exception.code(),
            "Fish content is invalid", List.of(), request);
}
```

Map the three catalog unique constraint names to `409 CATALOG_ENTRY_CONFLICT`, retaining the existing duplicate-email mapping. Do not return SQL messages, constraint values, or submitted content.

- [ ] **Step 9: Run API, catalog, and error-contract tests and verify GREEN**

Run:

```bash
cd backend
./mvnw -Dtest=AdminFishApiIntegrationTest,AdminAuthorizationTest,FishCatalogApiIntegrationTest,IdentityErrorContractTest test
```

Expected: all selected tests pass with stable response codes and safe messages.

- [ ] **Step 10: Commit the backend management API slice**

```bash
git add backend/src/main/java/com/fishbook/administration \
  backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java \
  backend/src/test/java/com/fishbook/administration \
  backend/src/test/java/com/fishbook/identity/web/AdminAuthorizationTest.java
git commit -m "feat: expose fish administration API"
```

---

### Task 5: Add the Administrator Route Guard and Navigation Entry

**Files:**
- Create: `frontend/src/features/auth/components/AdminRoute.tsx`
- Create: `frontend/src/features/auth/components/AdminRoute.test.tsx`
- Modify: `frontend/src/features/auth/components/SessionNav.tsx`
- Modify: `frontend/src/features/auth/components/SessionNav.test.tsx`

**Interfaces:**
- Consumes: existing `currentUserQueryConfig`, `fetchCurrentUser`, `ApiError`, and safe `returnTo` login flow.
- Produces: `<AdminRoute>` for Task 6 routes and an admin-only `/admin/fishes` navigation link.

- [ ] **Step 1: Write failing guard and navigation tests**

Model tests after `ProtectedRoute.test.tsx`:

```tsx
test('administrator session renders management content', async () => {
  currentUserMock.mockResolvedValue({
    id: 1,
    email: 'admin@example.com',
    nickname: '管理员',
    role: 'ADMIN',
  } satisfies User);

  renderAdminRoute('/admin/fishes?status=DRAFT');

  expect(await screen.findByRole('heading', { name: '管理内容' })).toBeInTheDocument();
});

test('ordinary user sees a local forbidden page without rendering children', async () => {
  currentUserMock.mockResolvedValue({
    id: 2,
    email: 'angler@example.com',
    nickname: '钓友',
    role: 'USER',
  } satisfies User);

  renderAdminRoute('/admin/fishes');

  expect(await screen.findByRole('heading', { name: '没有管理员权限' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '管理内容' })).not.toBeInTheDocument();
});
```

Also cover pending lookup, anonymous redirect preserving path/query/hash, non-auth lookup failure, admin link visible only to `ADMIN`, and the existing user navigation links for both roles.

- [ ] **Step 2: Run focused frontend tests and verify RED**

Run:

```bash
cd frontend
npm test -- --run src/features/auth/components/AdminRoute.test.tsx src/features/auth/components/SessionNav.test.tsx
```

Expected: missing `AdminRoute` and missing navigation link failures.

- [ ] **Step 3: Implement the role-aware guard and navigation**

Use the same loading, retry, and safe `401` redirect behavior as `ProtectedRoute`. On confirmed `401`, call the existing session-expiry helper so favorites, catches, current-user data, and—after Task 6—administrator queries are removed before redirecting. Then branch on role:

```tsx
if (currentUser.data.role !== 'ADMIN') {
  return (
    <main>
      <h1>没有管理员权限</h1>
      <p>此页面仅供管理员维护鱼类图鉴。</p>
      <Link to="/">返回首页</Link>
    </main>
  );
}

return children;
```

In `SessionNav`, render `<Link to="/admin/fishes">图鉴管理</Link>` only when `currentUser.data.role === 'ADMIN'`.

- [ ] **Step 4: Run focused frontend tests and verify GREEN**

Run:

```bash
cd frontend
npm test -- --run src/features/auth/components/AdminRoute.test.tsx src/features/auth/components/SessionNav.test.tsx
```

Expected: both test files pass.

- [ ] **Step 5: Commit the frontend authorization shell**

```bash
git add frontend/src/features/auth/components/AdminRoute.tsx \
  frontend/src/features/auth/components/AdminRoute.test.tsx \
  frontend/src/features/auth/components/SessionNav.tsx \
  frontend/src/features/auth/components/SessionNav.test.tsx
git commit -m "feat: guard administrator routes"
```

---

### Task 6: Build the Typed Management Client and URL-Driven List

**Files:**
- Create: `frontend/src/features/administration/model/types.ts`
- Create: `frontend/src/features/administration/model/adminFishSearchParams.ts`
- Create: `frontend/src/features/administration/model/adminFishSearchParams.test.ts`
- Create: `frontend/src/features/administration/api/adminFishApi.ts`
- Create: `frontend/src/features/administration/api/adminFishApi.test.ts`
- Create: `frontend/src/features/administration/pages/AdminFishListPage.tsx`
- Create: `frontend/src/features/administration/pages/AdminFishListPage.test.tsx`
- Create: `frontend/src/features/administration/pages/AdminFishPages.module.css`
- Modify: `frontend/src/features/auth/api/sessionCache.ts`
- Modify: `frontend/src/features/auth/api/sessionCache.test.ts`
- Modify: `frontend/src/app/router.tsx`

**Interfaces:**
- Consumes: the six API endpoints from Task 4 and `<AdminRoute>` from Task 5.
- Produces: administrator model types, query keys, all management request functions, and `/admin/fishes` list route for Task 7.

- [ ] **Step 1: Write failing URL normalization tests**

Define filters as `{ q: string; status: PublicationStatus | ''; page: number }` and test:

```ts
test('normalizes query status and page while dropping unsupported values', () => {
  expect(parseAdminFishSearchParams(new URLSearchParams(
    'q=%20%E9%B2%A4%20&status=draft&page=2&size=99',
  ))).toEqual({ q: '鲤', status: 'DRAFT', page: 2 });
});

test('serializes only non-default filters', () => {
  expect(toAdminFishSearchParams({ q: '', status: '', page: 0 }).toString()).toBe('');
  expect(toAdminFishSearchParams({ q: '鲤', status: 'UNPUBLISHED', page: 1 }).toString())
    .toBe('q=%E9%B2%A4&status=UNPUBLISHED&page=1');
});
```

Invalid/negative/unsafe integer pages normalize to `0`; unsupported statuses normalize to `''`; keywords trim and cap at 100 code points.

- [ ] **Step 2: Run model tests and verify RED**

Run: `cd frontend && npm test -- --run src/features/administration/model/adminFishSearchParams.test.ts`

Expected: module-not-found failure.

- [ ] **Step 3: Implement model types and URL parsing**

Use these core types:

```ts
export type PublicationStatus = 'DRAFT' | 'PUBLISHED' | 'UNPUBLISHED';

export type AdminFishFilters = {
  q: string;
  status: PublicationStatus | '';
  page: number;
};

export type AdminFishSummary = {
  id: number;
  slug: string;
  commonNameZh: string;
  scientificName: string;
  status: PublicationStatus;
  updatedAt: string;
};

export type AdminFishPage = {
  items: AdminFishSummary[];
  page: number;
  size: 20;
  totalItems: number;
  totalPages: number;
};

export type AdminFishContentInput = {
  commonNameZh: string;
  scientificName: string;
  familyNameZh: string;
  familyScientificName: string;
  genusNameZh: string;
  genusScientificName: string;
  aliases: string[];
  habitats: HabitatCode[];
  appearance: string;
  sizeDescription: string;
  habitatDescription: string;
  distribution: string;
  description: string;
  imagePath: string;
  imageAltText: string;
  imageSourceUrl: string;
  imageAuthor: string;
  imageLicenseName: string;
  imageLicenseUrl: string;
  displayOrder: number;
};

export type AdminFishCreateInput = AdminFishContentInput & { slug: string };
export type AdminFishUpdateInput = AdminFishContentInput;

export type AdminFishDetail = AdminFishCreateInput & {
  id: number;
  status: PublicationStatus;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
};
```

Import `HabitatCode` from the public catalog model so the administrator and public features cannot disagree about persisted habitat names.

- [ ] **Step 4: Run model tests and verify GREEN**

Run: `cd frontend && npm test -- --run src/features/administration/model/adminFishSearchParams.test.ts`

Expected: all URL tests pass.

- [ ] **Step 5: Write failing API contract tests**

Mock `global.fetch` following existing catalog API tests and assert exact requests:

```ts
test('fetches a filtered management page without a size parameter', async () => {
  fetchMock.mockResolvedValue(jsonResponse(emptyPage));

  await fetchAdminFishPage({ q: '鲤', status: 'DRAFT', page: 2 });

  expect(fetchMock).toHaveBeenCalledWith(
    '/api/v1/admin/fishes?q=%E9%B2%A4&status=DRAFT&page=2',
    expect.objectContaining({ credentials: 'include' }),
  );
});

test('uses independent content and publication endpoints', async () => {
  fetchMock.mockResolvedValueOnce(jsonResponse(detail));
  await updateAdminFish(7, updateInput);
  expect(lastRequest()).toMatchObject({ url: '/api/v1/admin/fishes/7', method: 'PUT' });

  fetchMock.mockResolvedValueOnce(jsonResponse({ ...detail, status: 'PUBLISHED' }));
  await publishAdminFish(7);
  expect(lastRequest()).toMatchObject({ url: '/api/v1/admin/fishes/7/publish', method: 'POST' });
});
```

Cover list, detail, create, update, publish, and unpublish; verify unsafe calls obtain/send CSRF through the shared client.

- [ ] **Step 6: Run API tests and verify RED**

Run: `cd frontend && npm test -- --run src/features/administration/api/adminFishApi.test.ts`

Expected: module-not-found failure.

- [ ] **Step 7: Implement the API client and session-scoped query root**

Export:

```ts
export const ADMIN_FISHES_QUERY_KEY = ['admin-fishes'] as const;
export const adminFishPageQueryKey = (filters: AdminFishFilters) => [
  ...ADMIN_FISHES_QUERY_KEY, 'list', filters.q, filters.status, filters.page,
] as const;
export const adminFishDetailQueryKey = (id: number) => [
  ...ADMIN_FISHES_QUERY_KEY, 'detail', id,
] as const;
```

Implement `fetchAdminFishPage`, `fetchAdminFish`, `createAdminFish`, `updateAdminFish`, `publishAdminFish`, and `unpublishAdminFish` with `apiFetch`. Add `queryClient.removeQueries({ queryKey: ADMIN_FISHES_QUERY_KEY })` to `clearSessionScopedQueries`, and extend its test so administrator data is never retained after current-user data disappears.

- [ ] **Step 8: Run API and session-cache tests and verify GREEN**

Run:

```bash
cd frontend
npm test -- --run src/features/administration/api/adminFishApi.test.ts src/features/auth/api/sessionCache.test.ts
```

Expected: request contracts and administrator cache cleanup pass.

- [ ] **Step 9: Write failing management-list page tests**

Cover loading, rows, status labels, keyword submit, status change resetting page to zero, pagination, empty state, retryable safe error, and confirmed `401` redirect. A representative test:

```tsx
test('filters by status through the URL and resets the page', async () => {
  renderList('/admin/fishes?q=鲤&page=2');

  await user.selectOptions(await screen.findByLabelText('发布状态'), 'DRAFT');

  expect(screen.getByTestId('location')).toHaveTextContent(
    '/admin/fishes?q=%E9%B2%A4&status=DRAFT',
  );
});
```

- [ ] **Step 10: Run the list-page test and verify RED**

Run: `cd frontend && npm test -- --run src/features/administration/pages/AdminFishListPage.test.tsx`

Expected: module-not-found failure.

- [ ] **Step 11: Implement the list page and guarded route**

The page uses URL search params as its source of truth, `adminFishPageQueryKey(filters)`, a search form, a four-option status selector, semantic table markup on wide screens, and accessible previous/next controls. It links to `/admin/fishes/new` and `/admin/fishes/{id}/edit` and renders `SessionNav` in its header.

Add only the list route now:

```tsx
{
  path: '/admin/fishes',
  element: (
    <AdminRoute>
      <AdminFishListPage />
    </AdminRoute>
  ),
},
```

- [ ] **Step 12: Run list, router-adjacent, and TypeScript checks and verify GREEN**

Run:

```bash
cd frontend
npm test -- --run src/features/administration/pages/AdminFishListPage.test.tsx src/features/auth/components/AdminRoute.test.tsx
npm run build
```

Expected: focused tests and production build pass.

- [ ] **Step 13: Commit the administrator list slice**

```bash
git add frontend/src/features/administration \
  frontend/src/features/auth/api/sessionCache.ts \
  frontend/src/features/auth/api/sessionCache.test.ts \
  frontend/src/app/router.tsx
git commit -m "feat: list managed fish"
```

---

### Task 7: Add the Shared Fish Form and Publication Actions

**Files:**
- Create: `frontend/src/features/administration/model/adminFishForm.ts`
- Create: `frontend/src/features/administration/model/adminFishForm.test.ts`
- Create: `frontend/src/features/administration/components/AdminFishForm.tsx`
- Create: `frontend/src/features/administration/components/AdminFishForm.test.tsx`
- Create: `frontend/src/features/administration/pages/AdminFishNewPage.tsx`
- Create: `frontend/src/features/administration/pages/AdminFishNewPage.test.tsx`
- Create: `frontend/src/features/administration/pages/AdminFishEditPage.tsx`
- Create: `frontend/src/features/administration/pages/AdminFishEditPage.test.tsx`
- Modify: `frontend/src/features/administration/pages/AdminFishPages.module.css`
- Modify: `frontend/src/features/catalog/api/catalogApi.ts`
- Modify: `frontend/src/features/catalog/api/catalogApi.test.ts`
- Modify: `frontend/src/features/catchlog/pages/CatchNewPage.tsx`
- Modify: `frontend/src/features/catchlog/pages/CatchNewPage.test.tsx`
- Modify: `frontend/src/features/catchlog/pages/CatchEditPage.tsx`
- Modify: `frontend/src/features/catchlog/pages/CatchEditPage.test.tsx`
- Modify: `frontend/src/app/router.tsx`

**Interfaces:**
- Consumes: admin types/client/list query keys from Task 6 and backend field-error names from Task 4.
- Produces: `/admin/fishes/new` and `/admin/fishes/:id/edit`, complete content editing, explicit publish/unpublish, public-catalog invalidation, and complete published-fish options for catch forms after the catalog grows beyond one page.

- [ ] **Step 1: Write failing form-normalization tests**

Represent aliases as one per line in the UI and habitats as checkbox values:

```ts
test('normalizes every text field aliases habitats and display order', () => {
  expect(parseAdminFishForm({
    ...validFormValues,
    slug: '  test-fish  ',
    commonNameZh: '  测试鱼  ',
    aliasesText: ' 别名一\n\n别名二\n别名一 ',
    habitats: ['LAKE', 'RIVER'],
    displayOrder: '7',
  })).toMatchObject({
    slug: 'test-fish',
    commonNameZh: '测试鱼',
    aliases: ['别名一', '别名二'],
    habitats: ['LAKE', 'RIVER'],
    displayOrder: 7,
  });
});
```

Add one test per important boundary: canonical/120-character slug, required/schema-sized names, unique aliases of at most 100 code points, at least one habitat, 10,000-code-point text, local image path, URL-shaped source/license fields, and positive safe integer display order.

- [ ] **Step 2: Run form-model tests and verify RED**

Run: `cd frontend && npm test -- --run src/features/administration/model/adminFishForm.test.ts`

Expected: module-not-found failure.

- [ ] **Step 3: Implement the Zod schema and DTO transformation**

Export:

```ts
export type AdminFishFormValues = {
  slug: string;
  commonNameZh: string;
  scientificName: string;
  familyNameZh: string;
  familyScientificName: string;
  genusNameZh: string;
  genusScientificName: string;
  aliasesText: string;
  habitats: HabitatCode[];
  appearance: string;
  sizeDescription: string;
  habitatDescription: string;
  distribution: string;
  description: string;
  imagePath: string;
  imageAltText: string;
  imageSourceUrl: string;
  imageAuthor: string;
  imageLicenseName: string;
  imageLicenseUrl: string;
  displayOrder: string;
};

export function parseAdminFishForm(values: AdminFishFormValues): AdminFishCreateInput;
export function adminFishDetailToFormValues(fish: AdminFishDetail): AdminFishFormValues;
```

Use `.transform` only after validations so field paths remain the flat API/form property names.

- [ ] **Step 4: Run form-model tests and verify GREEN**

Run: `cd frontend && npm test -- --run src/features/administration/model/adminFishForm.test.ts`

Expected: all normalization and boundary cases pass.

- [ ] **Step 5: Write failing shared-form interaction tests**

Cover create/edit slug behavior, all field groups, client validation, server field-error mapping, conflict message, generic safe error, confirmed `401`, and duplicate-submit protection:

```tsx
test('edit mode renders slug read-only and submits normalized content once', async () => {
  const onSubmit = vi.fn().mockResolvedValue(undefined);
  const { user } = renderForm({ slugReadOnly: true, initialValues, onSubmit });

  expect(screen.getByLabelText('Slug')).toHaveAttribute('readonly');
  await user.clear(screen.getByLabelText('中文名'));
  await user.type(screen.getByLabelText('中文名'), '  新名称  ');
  await user.click(screen.getByRole('button', { name: '保存修改' }));

  expect(onSubmit).toHaveBeenCalledTimes(1);
  expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ commonNameZh: '新名称' }));
});
```

- [ ] **Step 6: Run component tests and verify RED**

Run: `cd frontend && npm test -- --run src/features/administration/components/AdminFishForm.test.tsx`

Expected: module-not-found failure.

- [ ] **Step 7: Implement the shared accessible form**

Use `React Hook Form`, `FormField`, and Zod mapping patterns from `CatchRecordForm`. Group fields with `<fieldset>` and `<legend>` for basic, taxonomy, content, image attribution, and ordering sections. Render five habitat checkboxes. Use a newline-delimited alias textarea. Map API `fieldErrors` only to known fields; map backend `aliases` to form `aliasesText`, while every other flat DTO field maps by the same name. Show `CATALOG_ENTRY_CONFLICT` as “Slug、中文名或学名已被使用”; never render backend messages for unknown or 5xx errors. The component owns submit pending state and accepts:

```ts
type AdminFishFormProps = {
  initialValues?: AdminFishFormValues;
  slugReadOnly?: boolean;
  submitLabel: string;
  onSubmit: (input: AdminFishCreateInput) => Promise<void>;
};
```

- [ ] **Step 8: Run component tests and verify GREEN**

Run: `cd frontend && npm test -- --run src/features/administration/components/AdminFishForm.test.tsx`

Expected: validation, error, accessibility, and pending-state tests pass.

- [ ] **Step 9: Write failing new/edit page tests**

For the new page, assert a successful create invalidates the management root, seeds the returned detail, and navigates to `/admin/fishes/{id}/edit` with a visible “草稿已保存” message.

For edit, cover load/missing/error, update without slug in the request, status-specific action button, publish, unpublish confirmation, canceled confirmation, republish, duplicate-action protection, `401` expiry, and both admin/public query invalidation:

```tsx
test('confirmed unpublish updates detail and invalidates admin and public catalog roots', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  unpublishMock.mockResolvedValue({ ...publishedDetail, status: 'UNPUBLISHED' });
  const { queryClient, user } = renderEdit('/admin/fishes/7/edit', publishedDetail);
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

  await user.click(await screen.findByRole('button', { name: '下架' }));

  expect(unpublishMock).toHaveBeenCalledWith(7);
  expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ADMIN_FISHES_QUERY_KEY });
  expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: FISH_CATALOG_QUERY_KEY });
});
```

Extend `catalogApi.test.ts` with a two-page response proving `fetchAllPublishedFishOptions()` requests page `0` and every remaining page exactly once and returns all summaries in page order. Extend `CatchNewPage.test.tsx` to prove a published fish from page two is selectable. Extend `CatchEditPage.test.tsx` to prove the current unpublished fish is appended as an “已下架” option and can be retained, while it is never offered by the new-catch page.

- [ ] **Step 10: Run page tests and verify RED**

Run:

```bash
cd frontend
npm test -- --run src/features/administration/pages/AdminFishNewPage.test.tsx src/features/administration/pages/AdminFishEditPage.test.tsx
```

Expected: module-not-found failures.

- [ ] **Step 11: Implement create/edit orchestration and cache invalidation**

Export `FISH_CATALOG_QUERY_KEY = ['fish-catalog'] as const` from `catalogApi.ts` and build existing list/detail/filter keys from it without changing their resulting arrays. Add:

```ts
export const fishOptionsQueryKey = [...FISH_CATALOG_QUERY_KEY, 'options'] as const;

export async function fetchAllPublishedFishOptions(): Promise<FishSummary[]> {
  const first = await fetchFishPage({ q: '', family: '', habitat: '', page: 0 });
  const remaining = await Promise.all(
    Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, index) => (
      fetchFishPage({ q: '', family: '', habitat: '', page: index + 1 })
    )),
  );
  return [first, ...remaining].flatMap((page) => page.items);
}
```

Use this query in both catch create and edit pages instead of loading only catalog page zero.

New-page success flow:

```ts
await queryClient.invalidateQueries({ queryKey: ADMIN_FISHES_QUERY_KEY });
queryClient.setQueryData(adminFishDetailQueryKey(created.id), created);
navigate(`/admin/fishes/${created.id}/edit`, {
  replace: true,
  state: { created: true },
});
```

Edit-page content and action success invalidate `ADMIN_FISHES_QUERY_KEY` and `FISH_CATALOG_QUERY_KEY`, then replace the detail cache with the returned response. Destructure `slug` before calling `updateAdminFish`. Use `window.confirm('下架后公众将无法看到这条鱼类资料，确认下架吗？')` only for unpublish. Render `INVALID_PUBLICATION_TRANSITION` as a safe page-level “鱼类状态已变化，请刷新后重试” message. Use the existing session-generation and `useSessionExpiry` patterns so a late response from an expired session cannot repopulate caches.

If an existing catch form loads a record whose fish is no longer in the public catalog, preserve that current fish as an extra selected option marked “已下架”; do not offer it on the new-catch page or for switching another record. Add the regression to `CatchEditPage.test.tsx`.

- [ ] **Step 12: Register routes and finish responsive styles**

Add:

```tsx
{
  path: '/admin/fishes/new',
  element: <AdminRoute><AdminFishNewPage /></AdminRoute>,
},
{
  path: '/admin/fishes/:id/edit',
  element: <AdminRoute><AdminFishEditPage /></AdminRoute>,
},
```

Keep labels visible, preserve keyboard focus outlines, make the form one column on narrow screens, and avoid horizontal page scrolling for the management table.

- [ ] **Step 13: Run administration frontend and catch-edit regression tests and verify GREEN**

Run:

```bash
cd frontend
npm test -- --run \
  src/features/administration/model/adminFishForm.test.ts \
  src/features/administration/components/AdminFishForm.test.tsx \
  src/features/administration/pages/AdminFishNewPage.test.tsx \
  src/features/administration/pages/AdminFishEditPage.test.tsx \
  src/features/catalog/api/catalogApi.test.ts \
  src/features/catchlog/pages/CatchNewPage.test.tsx \
  src/features/catchlog/pages/CatchEditPage.test.tsx
npm run build
```

Expected: all selected tests and TypeScript/Vite build pass.

- [ ] **Step 14: Commit the complete administrator UI**

```bash
git add frontend/src/features/administration \
  frontend/src/features/catalog/api/catalogApi.ts \
  frontend/src/features/catalog/api/catalogApi.test.ts \
  frontend/src/features/catchlog/pages/CatchNewPage.tsx \
  frontend/src/features/catchlog/pages/CatchNewPage.test.tsx \
  frontend/src/features/catchlog/pages/CatchEditPage.tsx \
  frontend/src/features/catchlog/pages/CatchEditPage.test.tsx \
  frontend/src/app/router.tsx
git commit -m "feat: manage fish publication in the UI"
```

---

### Task 8: Prove the Product Loop and Document Safe Operation

**Files:**
- Create: `e2e/tests/admin-fish-flow.spec.ts`
- Modify: `.env.example`
- Modify: `compose.full.yaml`
- Modify: `README.md`
- Modify: `docs/runbooks/local-development.md`

**Interfaces:**
- Consumes: complete backend and frontend administrator loop from Tasks 1–7.
- Produces: deployable local bootstrap configuration, real browser proof, and accurate operator/user documentation.

- [ ] **Step 1: Write the failing administrator Playwright flow**

Use the local-only values from `.env.example`, a timestamped fish slug, and separate browser contexts for administrator, visitor, and ordinary user:

```ts
test('administrator publishes and unpublishes a fish while public visibility follows', async ({ browser, baseURL }) => {
  if (!baseURL) throw new Error('Playwright baseURL is required');
  const unique = Date.now();
  const slug = `admin-flow-fish-${unique}`;
  const name = `后台测试鱼${unique}`;

  const adminContext = await browser.newContext({ baseURL });
  const admin = await adminContext.newPage();
  await admin.goto('/login');
  await admin.getByLabel('邮箱').fill('admin@fishbook.local');
  await admin.getByLabel('密码').fill('fishbook_admin_local_only');
  await admin.getByRole('button', { name: '登录' }).click();
  await admin.getByRole('link', { name: '图鉴管理' }).click();
  await admin.getByRole('link', { name: '新建鱼类' }).click();

  await fillValidFishForm(admin, { slug, name });
  await admin.getByRole('button', { name: '保存草稿' }).click();
  await expect(admin.getByText('草稿已保存')).toBeVisible();

  const visitorContext = await browser.newContext({ baseURL });
  const visitor = await visitorContext.newPage();
  await visitor.goto(`/?q=${encodeURIComponent(name)}`);
  await expect(visitor.getByText(name)).toHaveCount(0);
  await visitor.goto(`/fish/${slug}`);
  await expect(visitor.getByRole('heading', { name: '没有找到这种鱼' })).toBeVisible();

  await admin.getByRole('button', { name: '发布' }).click();
  await expect(admin.getByRole('button', { name: '下架' })).toBeVisible();
  await visitor.goto(`/?q=${encodeURIComponent(name)}`);
  await expect(visitor.getByText(name)).toBeVisible();
  await visitor.goto(`/fish/${slug}`);
  await expect(visitor.getByRole('heading', { name })).toBeVisible();

  admin.once('dialog', (dialog) => dialog.accept());
  await admin.getByRole('button', { name: '下架' }).click();
  await expect(admin.getByRole('button', { name: '发布' })).toBeVisible();
  await visitor.goto(`/?q=${encodeURIComponent(name)}`);
  await expect(visitor.getByText(name)).toHaveCount(0);
  await visitor.goto(`/fish/${slug}`);
  await expect(visitor.getByRole('heading', { name: '没有找到这种鱼' })).toBeVisible();

  await visitorContext.close();
  await adminContext.close();
});
```

Implement `fillValidFishForm` in the same spec with explicit label fills, five content fields, at least one habitat, `/images/fish/cyprinus-carpio.jpg`, complete attribution, and a positive display order. Add a second test that registers/logs in an ordinary user, sees the local “没有管理员权限” page, and observes `403` from `fetch('/api/v1/admin/fishes')`.

- [ ] **Step 2: Run Playwright and verify RED**

With the existing full stack running, run: `cd e2e && npm test -- tests/admin-fish-flow.spec.ts`

Expected: FAIL because bootstrap environment, routes, or the complete lifecycle is not yet wired into the running stack.

- [ ] **Step 3: Wire local-only bootstrap configuration**

Add these non-production sample values to `.env.example`:

```dotenv
FISHBOOK_ADMIN_BOOTSTRAP_ENABLED=true
FISHBOOK_ADMIN_EMAIL=admin@fishbook.local
FISHBOOK_ADMIN_PASSWORD=fishbook_admin_local_only
FISHBOOK_ADMIN_NICKNAME=本地管理员
```

Pass the same four variables from the host environment into `compose.full.yaml`. Do not add them to the base database-only `compose.yaml` because it does not run the backend.

- [ ] **Step 4: Rebuild the full stack and verify the Playwright flow is GREEN**

Run:

```bash
docker compose --env-file .env.example -f compose.yaml -f compose.full.yaml up -d --build
cd e2e
npm test -- tests/admin-fish-flow.spec.ts
```

Expected: both administrator lifecycle and ordinary-user denial pass. Do not remove volumes after the run.

- [ ] **Step 5: Write documentation assertions before editing prose**

Run this command and observe failure while README still claims administration is missing:

```bash
rg -n "管理员后台、图鉴新增与编辑尚未实现|admin management UI and catalog writes are not implemented" README.md
```

Expected: matches are present and must be replaced.

- [ ] **Step 6: Update README and local runbook**

Document in Chinese and English:

- delivered administrator bootstrap, management routes, draft/edit/publish/unpublish behavior, and public visibility;
- all four environment variables and their local-only example values;
- bootstrap disabled by default in application configuration;
- first-start procedure, login at `/login`, and management entry at `/admin/fishes`;
- recommendation to turn `FISHBOOK_ADMIN_BOOTSTRAP_ENABLED` off after initial creation;
- existing-admin no-op and ordinary-user email conflict startup failure;
- secrets must come from deployment secret storage and never from committed production `.env` files;
- cover upload, physical deletion, and complex RBAC remain out of scope;
- administrator work must never expose or mutate another user's private catches, favorites, or photos.

Remove the stale roadmap item claiming administrator initialization and role authorization are unimplemented. Keep future public-image upload and production deployment work clearly marked as future work.

- [ ] **Step 7: Run documentation and repository hygiene checks**

Run:

```bash
! rg -n "管理员后台、图鉴新增与编辑尚未实现|admin management UI and catalog writes are not implemented" README.md
rg -n "FISHBOOK_ADMIN_BOOTSTRAP_ENABLED|/admin/fishes|发布|下架" README.md docs/runbooks/local-development.md .env.example
git diff --check
```

Expected: no stale implementation claim, all operational terms present, and no whitespace errors.

- [ ] **Step 8: Run the full verification suite**

Run:

```bash
cd backend
./mvnw test

cd ../frontend
npm test
npm run lint
npm run build

cd ../e2e
npm test
```

Expected: Maven, Vitest, ESLint, Vite/TypeScript, and every Playwright test pass with zero failures.

- [ ] **Step 9: Inspect the final diff and commit the release proof**

Run:

```bash
git status --short
git diff --check
git diff --stat
```

Confirm only administrator-loop files and intentional compatibility changes are present, then commit:

```bash
git add .env.example compose.full.yaml README.md docs/runbooks/local-development.md \
  e2e/tests/admin-fish-flow.spec.ts
git commit -m "test: verify fish administration loop"
```
