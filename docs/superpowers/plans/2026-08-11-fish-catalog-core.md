# FishBook Fish Catalog Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a public, read-only catalog of 12 verified Chinese freshwater fish with relational search/filter data, licensed local images, list/detail APIs, responsive React pages, and end-to-end coverage.

**Architecture:** Add a new modular-monolith `catalog` slice whose pure Java domain is accessed through an application query service and a repository port; JPA/MySQL and HTTP are adapters. The React catalog feature treats URL parameters as the source of truth and TanStack Query as the server-state cache. Flyway owns both schema and curated seed data, while Nginx serves audited local images from the same origin.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Web MVC, Spring Data JPA, Spring Security 7.1, Flyway, MySQL 8.4, Testcontainers 2.0.5, React 19.2, TypeScript 5.9, React Router 7.18, TanStack Query 5.101, Vitest 4.1, Testing Library, Playwright 1.62, Docker Compose.

## Global Constraints

- Before every command or edit, explain in Chinese what will be changed, why it is the next smallest step, and what observable result is expected.
- Execute feature work in an isolated worktree created with `superpowers:using-git-worktrees`; do not develop directly on `main`.
- Use RED → GREEN → REFACTOR for every behavior. Never write production implementation before the corresponding failing test is observed.
- Use Java 21 and the repository-pinned Spring Boot `4.1.0`; add no backend dependency unless a verified framework gap makes it necessary and the user approves.
- Use Node `24.18.0` and the exact packages already locked in `frontend/package-lock.json`; add no frontend state, search, or UI dependency.
- Keep `catalog.domain` free of Spring, JPA, Web, and Jackson annotations.
- Controllers call application services, never Spring Data repositories; API responses never expose JPA entities.
- Catalog scope is public read-only. Implement no POST/PUT/PATCH/DELETE controller, admin CRUD, upload, MinIO integration, favorites, catches, fishing advice, or runtime external fish API.
- Public access applies only to `GET /api/v1/fish` and `GET /api/v1/fish/**`; all write methods remain denied.
- Search covers Chinese formal name, alias, and scientific name. Filters cover exact Chinese family name and habitat code. Combined conditions use AND.
- Page numbering starts at `0`; page size is fixed at `12`; an explicit `size` parameter returns `400 INVALID_CATALOG_QUERY`.
- The seed catalog contains exactly: 鲫、鲤、草鱼、青鱼、鲢、鳙、乌鳢、鳜、黄颡鱼、团头鲂、翘嘴鲌、泥鳅.
- Every local image must have a verified original file page, author, source URL, license name, and license URL; ambiguous licensing blocks that asset from the seed migration.
- Preserve all existing registration, login, profile, logout, CSRF, session-cookie, health, Docker, and CI behavior.
- Do not delete Docker volumes during verification.

## File Map

### Backend domain and application

- `backend/src/main/java/com/fishbook/catalog/domain/HabitatType.java`: stable habitat codes and Chinese labels.
- `backend/src/main/java/com/fishbook/catalog/domain/ImageAttribution.java`: local image path plus attribution/license invariant.
- `backend/src/main/java/com/fishbook/catalog/domain/FishSpecies.java`: pure catalog aggregate.
- `backend/src/main/java/com/fishbook/catalog/domain/FishSearchCriteria.java`: normalized repository query criteria.
- `backend/src/main/java/com/fishbook/catalog/domain/FishPage.java`: framework-independent page result.
- `backend/src/main/java/com/fishbook/catalog/domain/FishRepository.java`: read-only repository port.
- `backend/src/main/java/com/fishbook/catalog/domain/FishNotFoundException.java`: missing-slug domain error with code `FISH_NOT_FOUND`.
- `backend/src/main/java/com/fishbook/catalog/application/FishCatalogQuery.java`: validates and normalizes raw HTTP-like query values without depending on Web types.
- `backend/src/main/java/com/fishbook/catalog/application/InvalidCatalogQueryException.java`: query-boundary error with code `INVALID_CATALOG_QUERY`.
- `backend/src/main/java/com/fishbook/catalog/application/FishCatalogQueryService.java`: catalog use-case interface.
- `backend/src/main/java/com/fishbook/catalog/application/DefaultFishCatalogQueryService.java`: transactionally executes search/filter/detail reads.
- `backend/src/main/java/com/fishbook/catalog/application/FishSummaryView.java`: list item view.
- `backend/src/main/java/com/fishbook/catalog/application/FishPageView.java`: page view.
- `backend/src/main/java/com/fishbook/catalog/application/FishDetailView.java`: detail view.
- `backend/src/main/java/com/fishbook/catalog/application/ImageAttributionView.java`: public image attribution view.
- `backend/src/main/java/com/fishbook/catalog/application/HabitatOptionView.java`: code/label pair.
- `backend/src/main/java/com/fishbook/catalog/application/FishFilterOptionsView.java`: available families and habitats.

### Backend persistence and Web

- `backend/src/main/java/com/fishbook/catalog/persistence/FishSpeciesJpaEntity.java`: species table mapping and lazy child sets.
- `backend/src/main/java/com/fishbook/catalog/persistence/FishAliasJpaEntity.java`: alias mapping.
- `backend/src/main/java/com/fishbook/catalog/persistence/FishHabitatId.java`: composite habitat key.
- `backend/src/main/java/com/fishbook/catalog/persistence/FishHabitatJpaEntity.java`: habitat association mapping.
- `backend/src/main/java/com/fishbook/catalog/persistence/SpringDataFishSpeciesJpaRepository.java`: ID-page query, detail graph, family query.
- `backend/src/main/java/com/fishbook/catalog/persistence/JpaFishRepositoryAdapter.java`: port adapter and deterministic ID-order reconstruction.
- `backend/src/main/java/com/fishbook/catalog/web/FishCatalogController.java`: three public GET endpoints.
- `backend/src/main/java/com/fishbook/catalog/web/dto/FishPageResponse.java`: paginated list response.
- `backend/src/main/java/com/fishbook/catalog/web/dto/FishSummaryResponse.java`: public card response.
- `backend/src/main/java/com/fishbook/catalog/web/dto/FishDetailResponse.java`: public detail response.
- `backend/src/main/java/com/fishbook/catalog/web/dto/FishFilterOptionsResponse.java`: filter response.
- `backend/src/main/java/com/fishbook/catalog/web/dto/HabitatOptionResponse.java`: habitat code/label response.
- `backend/src/main/java/com/fishbook/catalog/web/dto/ImageAttributionResponse.java`: public image attribution response.
- `backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java`: maps the two catalog errors without leaking internals.
- `backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java`: method-scoped public GET matchers.
- `backend/src/main/resources/db/migration/V3__create_fish_catalog.sql`: relational schema.
- `backend/src/main/resources/db/migration/V4__seed_fish_catalog.sql`: exactly 12 audited records.

### Backend tests

- `backend/src/test/java/com/fishbook/catalog/domain/FishSpeciesTest.java`: aggregate and attribution invariants.
- `backend/src/test/java/com/fishbook/catalog/persistence/CatalogDatabaseMigrationTest.java`: V3/V4 structure and seed contract.
- `backend/src/test/java/com/fishbook/catalog/persistence/JpaFishRepositoryAdapterTest.java`: real-MySQL search/filter/page/detail behavior.
- `backend/src/test/java/com/fishbook/catalog/application/DefaultFishCatalogQueryServiceTest.java`: normalization, mapping, and errors with a fake port.
- `backend/src/test/java/com/fishbook/catalog/web/FishCatalogApiIntegrationTest.java`: real-MySQL MockMvc public contract.
- `backend/src/test/java/com/fishbook/catalog/web/CatalogAuthorizationTest.java`: anonymous GET and denied writes.

### Content and frontend

- `docs/data-sources/fish-catalog-attribution.md`: one auditable row per fish for taxonomy, content, and image provenance.
- `frontend/public/images/fish/`: contains only the 12 slug-named JPEG files enumerated in Task 4.
- `frontend/src/features/catalog/model/types.ts`: API/domain types.
- `frontend/src/features/catalog/model/catalogSearchParams.ts`: URL parse/serialize normalization.
- `frontend/src/features/catalog/api/catalogApi.ts`: typed list/filter/detail calls and query keys.
- `frontend/src/features/catalog/components/CatalogSearchForm.tsx`: draft input and submitted keyword.
- `frontend/src/features/catalog/components/CatalogFilters.tsx`: family/habitat selectors and reset.
- `frontend/src/features/catalog/components/FishCard.tsx`: accessible card and image fallback.
- `frontend/src/features/catalog/components/CatalogPagination.tsx`: zero-based data to one-based UI navigation.
- `frontend/src/features/catalog/pages/FishCatalogPage.tsx`: URL-driven list orchestration.
- `frontend/src/features/catalog/pages/FishDetailPage.tsx`: slug-driven detail orchestration.
- `frontend/src/features/catalog/pages/FishCatalogPage.module.css`: responsive catalog layout.
- `frontend/src/features/catalog/pages/FishDetailPage.module.css`: readable detail layout and attribution.
- `frontend/src/app/App.tsx`: make the catalog the home page.
- `frontend/src/app/router.tsx`: add `/fish/:slug`.
- `frontend/src/features/catalog/catalogAssets.test.ts`: local asset existence contract.
- `frontend/src/features/catalog/model/catalogSearchParams.test.ts`: URL normalization contract.
- `frontend/src/features/catalog/api/catalogApi.test.ts`: typed HTTP adapter contract.
- `frontend/src/features/catalog/pages/FishCatalogPage.test.tsx`: list, search, filter, page, empty/error, and accessibility behavior.
- `frontend/src/features/catalog/pages/FishDetailPage.test.tsx`: detail, attribution, missing/error, and deep-link behavior.
- `e2e/tests/catalog-flow.spec.ts`: real anonymous list/search/filter/detail flow.
- `docs/runbooks/local-development.md`: catalog smoke checks and attribution pointer.

---

### Task 1: Catalog Schema and Pure Domain Model

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__create_fish_catalog.sql`
- Create: `backend/src/main/java/com/fishbook/catalog/domain/HabitatType.java`
- Create: `backend/src/main/java/com/fishbook/catalog/domain/ImageAttribution.java`
- Create: `backend/src/main/java/com/fishbook/catalog/domain/FishSpecies.java`
- Create: `backend/src/test/java/com/fishbook/catalog/domain/FishSpeciesTest.java`
- Create: `backend/src/test/java/com/fishbook/catalog/persistence/CatalogDatabaseMigrationTest.java`

**Interfaces:**
- Consumes: existing Flyway/MySQL Testcontainers bootstrap from `MySqlTestConfiguration`.
- Produces: `HabitatType`, `ImageAttribution`, and the `FishSpecies` canonical constructor for persistence and application tasks; V3 tables for later adapters.

- [ ] **Step 1: Write the failing MySQL migration test**

Create `CatalogDatabaseMigrationTest` with Spring Boot and the real MySQL test configuration. Assert all three tables exist, the two unique constraints exist, and `fish_habitats` has a composite primary key:

```java
@SpringBootTest
@Import(MySqlTestConfiguration.class)
class CatalogDatabaseMigrationTest {
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void createsCatalogTablesAndKeys() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);
        assertThat(tables).contains("fish_species", "fish_aliases", "fish_habitats");

        List<String> constraints = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name = 'fish_species'",
                String.class);
        assertThat(constraints).contains(
                "uk_fish_species_slug",
                "uk_fish_species_common_name_zh",
                "uk_fish_species_scientific_name");

        Integer primaryKeyColumns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() AND table_name = 'fish_habitats' "
                        + "AND constraint_name = 'PRIMARY'",
                Integer.class);
        assertThat(primaryKeyColumns).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Run the focused test to prove RED**

Run:

```bash
cd backend
./mvnw -Dtest=CatalogDatabaseMigrationTest test
```

Expected: FAIL because `fish_species`, `fish_aliases`, and `fish_habitats` do not exist.

- [ ] **Step 3: Add the V3 schema migration**

Use this schema shape; keep constraint/index names exact because later tests inspect them:

```sql
CREATE TABLE fish_species (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slug VARCHAR(120) NOT NULL,
    common_name_zh VARCHAR(100) NOT NULL,
    scientific_name VARCHAR(160) NOT NULL,
    family_name_zh VARCHAR(100) NOT NULL,
    family_scientific_name VARCHAR(160) NOT NULL,
    genus_name_zh VARCHAR(100) NOT NULL,
    genus_scientific_name VARCHAR(160) NOT NULL,
    appearance TEXT NOT NULL,
    size_description TEXT NOT NULL,
    habitat_description TEXT NOT NULL,
    distribution TEXT NOT NULL,
    description TEXT NOT NULL,
    image_path VARCHAR(255) NOT NULL,
    image_alt_text VARCHAR(255) NOT NULL,
    image_source_url VARCHAR(1000) NOT NULL,
    image_author VARCHAR(255) NOT NULL,
    image_license_name VARCHAR(100) NOT NULL,
    image_license_url VARCHAR(1000) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_fish_species PRIMARY KEY (id),
    CONSTRAINT uk_fish_species_slug UNIQUE (slug),
    CONSTRAINT uk_fish_species_common_name_zh UNIQUE (common_name_zh),
    CONSTRAINT uk_fish_species_scientific_name UNIQUE (scientific_name),
    CONSTRAINT ck_fish_species_display_order CHECK (display_order > 0),
    INDEX ix_fish_species_display_order (display_order, id),
    INDEX ix_fish_species_family_name_zh (family_name_zh)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fish_aliases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fish_species_id BIGINT NOT NULL,
    alias VARCHAR(100) NOT NULL,
    CONSTRAINT pk_fish_aliases PRIMARY KEY (id),
    CONSTRAINT uk_fish_aliases_species_alias UNIQUE (fish_species_id, alias),
    CONSTRAINT fk_fish_aliases_species FOREIGN KEY (fish_species_id)
        REFERENCES fish_species (id) ON DELETE CASCADE,
    INDEX ix_fish_aliases_alias (alias)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fish_habitats (
    fish_species_id BIGINT NOT NULL,
    habitat_code VARCHAR(20) NOT NULL,
    CONSTRAINT pk_fish_habitats PRIMARY KEY (fish_species_id, habitat_code),
    CONSTRAINT fk_fish_habitats_species FOREIGN KEY (fish_species_id)
        REFERENCES fish_species (id) ON DELETE CASCADE,
    CONSTRAINT ck_fish_habitats_code CHECK (
        habitat_code IN ('RIVER', 'LAKE', 'RESERVOIR', 'POND', 'STREAM')
    ),
    INDEX ix_fish_habitats_code (habitat_code, fish_species_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- [ ] **Step 4: Run the migration test to prove GREEN**

Run `./mvnw -Dtest=CatalogDatabaseMigrationTest test` from `backend`.

Expected: PASS with Flyway applying V3 to MySQL 8.4.

- [ ] **Step 5: Write failing domain invariant tests**

Cover the exact public invariants:

```java
@Test
void rejectsNonCanonicalSlug() {
    assertThatThrownBy(() -> fish("Cyprinus carpio"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("slug");
}

@Test
void imageMustBeLocalAndFullyAttributed() {
    assertThatThrownBy(() -> new ImageAttribution(
            "https://remote.example/carp.jpg", "鲤", "source", "author", "CC BY 4.0", "license"))
            .isInstanceOf(IllegalArgumentException.class);
}

@Test
void defensivelyCopiesAliasesAndHabitats() {
    List<String> aliases = new ArrayList<>(List.of("鲤鱼"));
    Set<HabitatType> habitats = new HashSet<>(Set.of(HabitatType.RIVER));
    FishSpecies fish = fish("cyprinus-carpio", aliases, habitats);
    aliases.add("污染值");
    habitats.add(HabitatType.POND);
    assertThat(fish.aliases()).containsExactly("鲤鱼");
    assertThat(fish.habitats()).containsExactly(HabitatType.RIVER);
}
```

The fixture supplies nonblank values for every remaining field and an image path under `/images/fish/`.

```java
private static FishSpecies fish(String slug) {
    return fish(slug, List.of("鲤鱼"), Set.of(HabitatType.RIVER));
}

private static FishSpecies fish(
        String slug, List<String> aliases, Set<HabitatType> habitats) {
    Instant now = Instant.parse("2026-08-11T00:00:00Z");
    return new FishSpecies(
            1L, slug, "鲤", "Cyprinus carpio", "鲤科", "Cyprinidae",
            "鲤属", "Cyprinus", aliases, habitats,
            "体形呈纺锤形。", "常见个体为中型鱼。", "生活在淡水水域。",
            "分布于中国多地。", "常见淡水鱼。",
            new ImageAttribution(
                    "/images/fish/cyprinus-carpio.jpg",
                    "鲤（Cyprinus carpio）",
                    "https://commons.wikimedia.org/wiki/File:Cyprinus_carpio.jpeg",
                    "Test Author", "CC BY 4.0",
                    "https://creativecommons.org/licenses/by/4.0/"),
            1, now, now);
}
```

- [ ] **Step 6: Run the domain test to prove RED**

Run:

```bash
./mvnw -Dtest=FishSpeciesTest test
```

Expected: test compilation FAIL because the catalog domain types do not exist.

- [ ] **Step 7: Implement the minimal pure domain types**

Use this enum contract:

```java
public enum HabitatType {
    RIVER("江河"),
    LAKE("湖泊"),
    RESERVOIR("水库"),
    POND("池塘"),
    STREAM("溪流");

    private final String labelZh;

    HabitatType(String labelZh) { this.labelZh = labelZh; }
    public String labelZh() { return labelZh; }
}
```

`ImageAttribution` must reject blank fields and paths not matching `^/images/fish/[a-z0-9-]+\\.(jpg|jpeg|png|webp)$`.

```java
public record ImageAttribution(
        String path,
        String altText,
        String sourceUrl,
        String author,
        String licenseName,
        String licenseUrl) {
    public ImageAttribution {
        requireText(path, "path");
        requireText(altText, "altText");
        requireText(sourceUrl, "sourceUrl");
        requireText(author, "author");
        requireText(licenseName, "licenseName");
        requireText(licenseUrl, "licenseUrl");
        if (!path.matches("/images/fish/[a-z0-9-]+\\.(jpg|jpeg|png|webp)")) {
            throw new IllegalArgumentException("path must be a local fish image");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
```

`FishSpecies` uses this exact public shape:

```java
public record FishSpecies(
        Long id,
        String slug,
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
        int displayOrder,
        Instant createdAt,
        Instant updatedAt) {

    public FishSpecies {
        requireText(slug, "slug");
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("slug must be canonical");
        }
        requireText(commonNameZh, "commonNameZh");
        requireText(scientificName, "scientificName");
        requireText(familyNameZh, "familyNameZh");
        requireText(familyScientificName, "familyScientificName");
        requireText(genusNameZh, "genusNameZh");
        requireText(genusScientificName, "genusScientificName");
        requireText(appearance, "appearance");
        requireText(sizeDescription, "sizeDescription");
        requireText(habitatDescription, "habitatDescription");
        requireText(distribution, "distribution");
        requireText(description, "description");
        Objects.requireNonNull(image, "image must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases must not be null"));
        if (aliases.stream().anyMatch(alias -> alias == null || alias.isBlank())) {
            throw new IllegalArgumentException("aliases must not contain blanks");
        }
        if (aliases.stream().distinct().count() != aliases.size()) {
            throw new IllegalArgumentException("aliases must be unique");
        }
        habitats = Set.copyOf(Objects.requireNonNull(habitats, "habitats must not be null"));
        if (habitats.isEmpty()) throw new IllegalArgumentException("habitats must not be empty");
        if (displayOrder <= 0) throw new IllegalArgumentException("displayOrder must be positive");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
```

- [ ] **Step 8: Run focused and existing migration tests**

Run:

```bash
./mvnw -Dtest=FishSpeciesTest,CatalogDatabaseMigrationTest,DatabaseMigrationTest test
```

Expected: PASS, with no change to identity/session tables.

- [ ] **Step 9: Commit Task 1**

```bash
git add backend/src/main/resources/db/migration/V3__create_fish_catalog.sql \
  backend/src/main/java/com/fishbook/catalog/domain \
  backend/src/test/java/com/fishbook/catalog/domain \
  backend/src/test/java/com/fishbook/catalog/persistence/CatalogDatabaseMigrationTest.java
git commit -m "feat: add fish catalog schema and domain"
```

### Task 2: Real-MySQL Read Repository Adapter

**Files:**
- Create: `backend/src/main/java/com/fishbook/catalog/domain/FishSearchCriteria.java`
- Create: `backend/src/main/java/com/fishbook/catalog/domain/FishPage.java`
- Create: `backend/src/main/java/com/fishbook/catalog/domain/FishRepository.java`
- Create: `backend/src/main/java/com/fishbook/catalog/persistence/FishSpeciesJpaEntity.java`
- Create: `backend/src/main/java/com/fishbook/catalog/persistence/FishAliasJpaEntity.java`
- Create: `backend/src/main/java/com/fishbook/catalog/persistence/FishHabitatId.java`
- Create: `backend/src/main/java/com/fishbook/catalog/persistence/FishHabitatJpaEntity.java`
- Create: `backend/src/main/java/com/fishbook/catalog/persistence/SpringDataFishSpeciesJpaRepository.java`
- Create: `backend/src/main/java/com/fishbook/catalog/persistence/JpaFishRepositoryAdapter.java`
- Create: `backend/src/test/java/com/fishbook/catalog/persistence/JpaFishRepositoryAdapterTest.java`

**Interfaces:**
- Consumes: V3 tables and Task 1 domain types.
- Produces: `FishRepository.search(FishSearchCriteria)`, `findBySlug(String)`, and `findAvailableFamilies()` for the application service.

- [ ] **Step 1: Write repository integration tests before mappings**

Use `@DataJpaTest`, `@Import({MySqlTestConfiguration.class, JpaFishRepositoryAdapter.class})`, `JdbcTemplate`, and `TestEntityManager`. In `@BeforeEach`, delete `fish_habitats`, `fish_aliases`, then `fish_species`, and insert three deterministic fixtures: 鲤 (`cyprinus-carpio`, alias 鲤鱼, RIVER/LAKE, order 1), 乌鳢 (`channa-argus`, alias 黑鱼, LAKE/POND, order 2), and 鳜 (`siniperca-chuatsi`, alias 桂花鱼, RIVER/RESERVOIR, order 3).

Use this helper so every persistence test inserts complete rows instead of bypassing V3 invariants:

```java
private void insertFish(
        long id,
        String slug,
        String commonName,
        String scientificName,
        String familyName,
        String alias,
        int displayOrder,
        HabitatType... habitats) {
    jdbcTemplate.update("""
            INSERT INTO fish_species (
                id, slug, common_name_zh, scientific_name,
                family_name_zh, family_scientific_name,
                genus_name_zh, genus_scientific_name,
                appearance, size_description, habitat_description,
                distribution, description,
                image_path, image_alt_text, image_source_url, image_author,
                image_license_name, image_license_url,
                display_order, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, 'Testidae', '测试属', 'Testgenus',
                '外形描述', '体型描述', '栖息环境描述', '分布描述', '综合介绍',
                ?, ?, 'https://commons.wikimedia.org/wiki/File:Test.jpg',
                'Test Author', 'CC BY 4.0',
                'https://creativecommons.org/licenses/by/4.0/',
                ?, '2026-08-11 00:00:00.000000', '2026-08-11 00:00:00.000000')
            """,
            id, slug, commonName, scientificName, familyName,
            "/images/fish/" + slug + ".jpg",
            commonName + "（" + scientificName + "）",
            displayOrder);
    jdbcTemplate.update(
            "INSERT INTO fish_aliases (fish_species_id, alias) VALUES (?, ?)",
            id, alias);
    for (HabitatType habitat : habitats) {
        jdbcTemplate.update(
                "INSERT INTO fish_habitats (fish_species_id, habitat_code) VALUES (?, ?)",
                id, habitat.name());
    }
}
```

Assert these behaviors in separate tests:

```java
assertThat(adapter.search(new FishSearchCriteria("鲤", null, null, 0, 12)).items())
        .extracting(FishSpecies::slug)
        .containsExactly("cyprinus-carpio");

assertThat(adapter.search(new FishSearchCriteria("黑鱼", null, null, 0, 12)).items())
        .extracting(FishSpecies::slug)
        .containsExactly("channa-argus");

assertThat(adapter.search(new FishSearchCriteria("CHAnNa ARGus", null, null, 0, 12)).items())
        .extracting(FishSpecies::slug)
        .containsExactly("channa-argus");

assertThat(adapter.search(new FishSearchCriteria(null, "鳜科", HabitatType.RESERVOIR, 0, 12)).items())
        .extracting(FishSpecies::slug)
        .containsExactly("siniperca-chuatsi");

assertThat(adapter.findBySlug("channa-argus")).get()
        .extracting(FishSpecies::aliases)
        .asList().contains("黑鱼");

assertThat(adapter.findAvailableFamilies()).containsExactly("鲤科", "鳜科", "鳢科");
```

The pagination test clears the three normal fixtures, inserts 13 deterministic rows, and checks both pages:

```java
for (int index = 1; index <= 13; index++) {
    insertFish(100L + index, "fixture-fish-" + index, "测试鱼" + index,
            "Testus fish" + index, "测试科", "别名" + index,
            index, HabitatType.RIVER);
}
FishPage first = adapter.search(new FishSearchCriteria(null, null, null, 0, 12));
FishPage second = adapter.search(new FishSearchCriteria(null, null, null, 1, 12));
assertThat(first.items()).hasSize(12);
assertThat(first.items().getFirst().slug()).isEqualTo("fixture-fish-1");
assertThat(first.totalItems()).isEqualTo(13);
assertThat(first.totalPages()).isEqualTo(2);
assertThat(second.items()).extracting(FishSpecies::slug)
        .containsExactly("fixture-fish-13");
```

- [ ] **Step 2: Run repository tests to prove RED**

Run:

```bash
./mvnw -Dtest=JpaFishRepositoryAdapterTest test
```

Expected: compilation FAIL because `FishSearchCriteria`, `FishPage`, `FishRepository`, and the adapter do not exist.

- [ ] **Step 3: Add framework-independent repository contracts**

```java
public record FishSearchCriteria(
        String query,
        String family,
        HabitatType habitat,
        int page,
        int size) {}

public record FishPage(
        List<FishSpecies> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
    public FishPage {
        items = List.copyOf(items);
    }
}

public interface FishRepository {
    FishPage search(FishSearchCriteria criteria);
    Optional<FishSpecies> findBySlug(String slug);
    List<String> findAvailableFamilies();
}
```

- [ ] **Step 4: Add JPA entities with explicit relationships**

Map `FishSpeciesJpaEntity` to every V3 column. Use two lazy `Set` associations so both can be loaded in one entity graph without a multiple-bag exception:

```java
@OneToMany(mappedBy = "fishSpecies", fetch = FetchType.LAZY)
private Set<FishAliasJpaEntity> aliases = new HashSet<>();

@OneToMany(mappedBy = "fishSpecies", fetch = FetchType.LAZY)
private Set<FishHabitatJpaEntity> habitats = new HashSet<>();
```

`FishAliasJpaEntity` uses an identity ID and `@ManyToOne(fetch = LAZY)` joined by `fish_species_id`.

`FishHabitatId` is `@Embeddable`, `Serializable`, contains `Long fishSpeciesId` and `HabitatType habitatCode`, and implements value-based `equals`/`hashCode`. `FishHabitatJpaEntity` uses `@EmbeddedId`, `@MapsId("fishSpeciesId")`, and `@Enumerated(EnumType.STRING)` for the code column.

- [ ] **Step 5: Implement the two-stage Spring Data query**

Use an ID page to avoid collection-join pagination errors:

```java
@Query(value = """
        select f.id from FishSpeciesJpaEntity f
        where (:pattern is null
            or lower(f.commonNameZh) like lower(:pattern) escape '\\'
            or lower(f.scientificName) like lower(:pattern) escape '\\'
            or exists (
                select a.id from FishAliasJpaEntity a
                where a.fishSpecies.id = f.id
                  and lower(a.alias) like lower(:pattern) escape '\\'
            ))
          and (:family is null or f.familyNameZh = :family)
          and (:habitat is null or exists (
                select h.id from FishHabitatJpaEntity h
                where h.fishSpecies.id = f.id and h.id.habitatCode = :habitat
          ))
        """,
        countQuery = """
        select count(f.id) from FishSpeciesJpaEntity f
        where (:pattern is null
            or lower(f.commonNameZh) like lower(:pattern) escape '\\'
            or lower(f.scientificName) like lower(:pattern) escape '\\'
            or exists (
                select a.id from FishAliasJpaEntity a
                where a.fishSpecies.id = f.id
                  and lower(a.alias) like lower(:pattern) escape '\\'
            ))
          and (:family is null or f.familyNameZh = :family)
          and (:habitat is null or exists (
                select h.id from FishHabitatJpaEntity h
                where h.fishSpecies.id = f.id and h.id.habitatCode = :habitat
          ))
        """)
Page<Long> searchIds(String pattern, String family, HabitatType habitat, Pageable pageable);

@EntityGraph(attributePaths = {"aliases", "habitats"})
@Query("select distinct f from FishSpeciesJpaEntity f where f.id in :ids")
List<FishSpeciesJpaEntity> findAllWithDetailsByIdIn(Collection<Long> ids);

@EntityGraph(attributePaths = {"aliases", "habitats"})
Optional<FishSpeciesJpaEntity> findBySlug(String slug);

@Query("select distinct f.familyNameZh from FishSpeciesJpaEntity f")
List<String> findAvailableFamilies();
```

The adapter escapes `\\`, `%`, and `_` before surrounding the query with `%`. Construct `PageRequest.of(page, size, Sort.by("displayOrder").ascending().and(Sort.by("id")))`. Reorder loaded entities using the ID page before converting them to domain records. Sort aliases and available families with Java natural order and habitats by enum declaration order during mapping for deterministic JSON independent of database collation.

- [ ] **Step 6: Run repository GREEN and inspect SQL behavior**

Run `./mvnw -Dtest=JpaFishRepositoryAdapterTest test`.

Expected: all name/alias/scientific/filter/pagination/detail/family cases PASS on MySQL 8.4. Confirm logs show one ID page query and one detail collection query per result page, not one child query per fish.

- [ ] **Step 7: Commit Task 2**

```bash
git add backend/src/main/java/com/fishbook/catalog/domain \
  backend/src/main/java/com/fishbook/catalog/persistence \
  backend/src/test/java/com/fishbook/catalog/persistence/JpaFishRepositoryAdapterTest.java
git commit -m "feat: add fish catalog query repository"
```

### Task 3: Query Application Service and Boundary Validation

**Files:**
- Create: `backend/src/main/java/com/fishbook/catalog/domain/FishNotFoundException.java`
- Create: `backend/src/main/java/com/fishbook/catalog/application/FishCatalogQuery.java`
- Create: `backend/src/main/java/com/fishbook/catalog/application/InvalidCatalogQueryException.java`
- Create: `backend/src/main/java/com/fishbook/catalog/application/FishCatalogQueryService.java`
- Create: `backend/src/main/java/com/fishbook/catalog/application/DefaultFishCatalogQueryService.java`
- Create: `backend/src/main/java/com/fishbook/catalog/application/FishSummaryView.java`
- Create: `backend/src/main/java/com/fishbook/catalog/application/FishPageView.java`
- Create: `backend/src/main/java/com/fishbook/catalog/application/FishDetailView.java`
- Create: `backend/src/main/java/com/fishbook/catalog/application/ImageAttributionView.java`
- Create: `backend/src/main/java/com/fishbook/catalog/application/HabitatOptionView.java`
- Create: `backend/src/main/java/com/fishbook/catalog/application/FishFilterOptionsView.java`
- Create: `backend/src/test/java/com/fishbook/catalog/application/DefaultFishCatalogQueryServiceTest.java`

**Interfaces:**
- Consumes: Task 2 `FishRepository`.
- Produces: `search(FishCatalogQuery)`, `getBySlug(String)`, and `getFilterOptions()` for the Web adapter.

- [ ] **Step 1: Write failing service tests with a recording fake repository**

The fake stores the last criteria and configurable page/detail/families. Cover:

```java
@Test
void trimsSearchAndFamilyAndUsesFixedPageSize() {
    service.search(FishCatalogQuery.from("  黑鱼  ", "  鳢科 ", "lake", "2", null));
    assertThat(repository.lastCriteria()).isEqualTo(
            new FishSearchCriteria("黑鱼", "鳢科", HabitatType.LAKE, 2, 12));
}

@Test
void blankSearchAndFamilyBecomeAbsent() {
    service.search(FishCatalogQuery.from("  ", "", null, null, null));
    assertThat(repository.lastCriteria().query()).isNull();
    assertThat(repository.lastCriteria().family()).isNull();
}

@ParameterizedTest
@ValueSource(strings = {"-1", "not-a-number"})
void rejectsInvalidPage(String page) {
    assertThatThrownBy(() -> FishCatalogQuery.from(null, null, null, page, null))
            .isInstanceOf(InvalidCatalogQueryException.class)
            .extracting("code").isEqualTo("INVALID_CATALOG_QUERY");
}

@Test
void rejectsExplicitSizeEvenWhenItIsTwelve() {
    assertThatThrownBy(() -> FishCatalogQuery.from(null, null, null, "0", "12"))
            .isInstanceOf(InvalidCatalogQueryException.class);
}

@Test
void missingSlugUsesStableErrorCode() {
    assertThatThrownBy(() -> service.getBySlug("missing-fish"))
            .isInstanceOf(FishNotFoundException.class)
            .extracting("code").isEqualTo("FISH_NOT_FOUND");
}
```

Add these exact service assertions:

```java
assertThatThrownBy(() -> FishCatalogQuery.from("鱼".repeat(101), null, null, null, null))
        .isInstanceOf(InvalidCatalogQueryException.class);
assertThatThrownBy(() -> FishCatalogQuery.from(null, "科".repeat(101), null, null, null))
        .isInstanceOf(InvalidCatalogQueryException.class);
assertThatThrownBy(() -> FishCatalogQuery.from(null, null, "SEA", null, null))
        .isInstanceOf(InvalidCatalogQueryException.class);

FishPageView page = service.search(FishCatalogQuery.from(null, null, null, null, null));
assertThat(page.items().getFirst().aliases()).contains("黑鱼");
assertThat(page.items().getFirst().habitats()).contains(
        new HabitatOptionView("LAKE", "湖泊"));
assertThat(page.items().getFirst().imagePath()).isEqualTo(
        "/images/fish/channa-argus.jpg");

assertThat(service.getFilterOptions().habitats())
        .extracting(HabitatOptionView::code)
        .containsExactly("RIVER", "LAKE", "RESERVOIR", "POND", "STREAM");
```

- [ ] **Step 2: Run the focused service test to prove RED**

Run `./mvnw -Dtest=DefaultFishCatalogQueryServiceTest test`.

Expected: compilation FAIL because application contracts are absent.

- [ ] **Step 3: Implement exact query normalization**

```java
public record FishCatalogQuery(
        String query,
        String family,
        HabitatType habitat,
        int page) {
    public static FishCatalogQuery from(
            String rawQuery,
            String rawFamily,
            String rawHabitat,
            String rawPage,
            String rawSize) {
        if (rawSize != null) throw new InvalidCatalogQueryException("size is fixed at 12");
        String query = normalizeOptional(rawQuery, "q");
        String family = normalizeOptional(rawFamily, "family");
        HabitatType habitat = parseHabitat(rawHabitat);
        int page = parsePage(rawPage);
        return new FishCatalogQuery(query, family, habitat, page);
    }

    private static String normalizeOptional(String raw, String field) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String normalized = raw.trim();
        if (normalized.codePointCount(0, normalized.length()) > 100) {
            throw new InvalidCatalogQueryException(field + " exceeds 100 characters");
        }
        return normalized;
    }

    private static HabitatType parseHabitat(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return HabitatType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidCatalogQueryException("habitat is unsupported");
        }
    }

    private static int parsePage(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed < 0) throw new NumberFormatException("negative page");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new InvalidCatalogQueryException("page must be a non-negative integer");
        }
    }
}
```

The helper code returns `null` for trimmed blank values, counts Unicode code points for the 100-character boundary, preserves search/family case, uppercases only habitat codes with `Locale.ROOT`, and treats a blank page as `0`.

- [ ] **Step 4: Add stable exceptions and view interfaces**

```java
public final class InvalidCatalogQueryException extends RuntimeException {
    public InvalidCatalogQueryException(String message) { super(message); }
    public String code() { return "INVALID_CATALOG_QUERY"; }
}

public final class FishNotFoundException extends RuntimeException {
    public FishNotFoundException(String slug) { super("Fish was not found: " + slug); }
    public String code() { return "FISH_NOT_FOUND"; }
}

public interface FishCatalogQueryService {
    FishPageView search(FishCatalogQuery query);
    FishDetailView getBySlug(String slug);
    FishFilterOptionsView getFilterOptions();
}

public record HabitatOptionView(String code, String labelZh) {}

public record ImageAttributionView(
        String path,
        String altText,
        String sourceUrl,
        String author,
        String licenseName,
        String licenseUrl) {}

public record FishSummaryView(
        String slug,
        String commonNameZh,
        String scientificName,
        String familyNameZh,
        List<String> aliases,
        List<HabitatOptionView> habitats,
        String imagePath,
        String imageAltText) {}

public record FishPageView(
        List<FishSummaryView> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {}

public record FishDetailView(
        String slug,
        String commonNameZh,
        String scientificName,
        String familyNameZh,
        String familyScientificName,
        String genusNameZh,
        String genusScientificName,
        List<String> aliases,
        List<HabitatOptionView> habitats,
        String appearance,
        String sizeDescription,
        String habitatDescription,
        String distribution,
        String description,
        ImageAttributionView image) {}

public record FishFilterOptionsView(
        List<String> families,
        List<HabitatOptionView> habitats) {}
```

Give every list-valued view record a compact constructor using `List.copyOf` so callers cannot mutate results after service mapping.

- [ ] **Step 5: Implement the transactional query service**

Annotate the non-final class `@Service` and each public read method `@Transactional(readOnly = true)`. Search with:

```java
FishPage page = repository.search(new FishSearchCriteria(
        query.query(), query.family(), query.habitat(), query.page(), 12));
return new FishPageView(
        page.items().stream().map(this::toSummary).toList(),
        page.page(), page.size(), page.totalItems(), page.totalPages());
```

`getBySlug` validates canonical nonblank slug syntax and throws `InvalidCatalogQueryException` for malformed slugs; a canonical but missing slug throws `FishNotFoundException`. `getFilterOptions` maps all `HabitatType.values()` and uses repository family order.

- [ ] **Step 6: Run service and repository tests**

Run:

```bash
./mvnw -Dtest=DefaultFishCatalogQueryServiceTest,JpaFishRepositoryAdapterTest test
```

Expected: PASS with the fake proving normalization and MySQL proving persistence behavior.

- [ ] **Step 7: Commit Task 3**

```bash
git add backend/src/main/java/com/fishbook/catalog/application \
  backend/src/main/java/com/fishbook/catalog/domain/FishNotFoundException.java \
  backend/src/test/java/com/fishbook/catalog/application
git commit -m "feat: add fish catalog query service"
```

### Task 4: Curated Seed Data, Licensed Images, and Provenance

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__seed_fish_catalog.sql`
- Modify: `backend/src/test/java/com/fishbook/catalog/persistence/CatalogDatabaseMigrationTest.java`
- Create: `docs/data-sources/fish-catalog-attribution.md`
- Create: `frontend/public/images/fish/carassius-auratus.jpg`
- Create: `frontend/public/images/fish/cyprinus-carpio.jpg`
- Create: `frontend/public/images/fish/ctenopharyngodon-idella.jpg`
- Create: `frontend/public/images/fish/mylopharyngodon-piceus.jpg`
- Create: `frontend/public/images/fish/hypophthalmichthys-molitrix.jpg`
- Create: `frontend/public/images/fish/hypophthalmichthys-nobilis.jpg`
- Create: `frontend/public/images/fish/channa-argus.jpg`
- Create: `frontend/public/images/fish/siniperca-chuatsi.jpg`
- Create: `frontend/public/images/fish/tachysurus-fulvidraco.jpg`
- Create: `frontend/public/images/fish/megalobrama-amblycephala.jpg`
- Create: `frontend/public/images/fish/culter-alburnus.jpg`
- Create: `frontend/public/images/fish/misgurnus-anguillicaudatus.jpg`
- Create: `frontend/src/features/catalog/catalogAssets.test.ts`

**Interfaces:**
- Consumes: V3 schema and domain image-path invariant.
- Produces: exactly 12 queryable rows and same-origin image paths for API/UI tasks.

- [ ] **Step 1: Extend the migration test and write the asset test before data**

Backend assertions:

```java
assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fish_species", Integer.class))
        .isEqualTo(12);
assertThat(jdbcTemplate.queryForList(
        "SELECT common_name_zh FROM fish_species ORDER BY display_order", String.class))
        .containsExactly("鲫", "鲤", "草鱼", "青鱼", "鲢", "鳙", "乌鳢", "鳜", "黄颡鱼", "团头鲂", "翘嘴鲌", "泥鳅");
assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM fish_species WHERE image_source_url = '' "
                + "OR image_author = '' OR image_license_name = '' OR image_license_url = ''",
        Integer.class)).isZero();
```

Frontend asset test:

```ts
import { existsSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, test } from 'vitest';

const slugs = [
  'carassius-auratus', 'cyprinus-carpio', 'ctenopharyngodon-idella',
  'mylopharyngodon-piceus', 'hypophthalmichthys-molitrix',
  'hypophthalmichthys-nobilis', 'channa-argus', 'siniperca-chuatsi',
  'tachysurus-fulvidraco', 'megalobrama-amblycephala',
  'culter-alburnus', 'misgurnus-anguillicaudatus',
] as const;

test('ships one non-empty local image for each seeded fish', () => {
  for (const slug of slugs) {
    const path = resolve(process.cwd(), 'public/images/fish', `${slug}.jpg`);
    expect(existsSync(path), path).toBe(true);
    expect(statSync(path).size, path).toBeGreaterThan(10_000);
  }
});
```

- [ ] **Step 2: Run both tests to prove RED**

Run:

```bash
cd backend
./mvnw -Dtest=CatalogDatabaseMigrationTest test
cd ../frontend
npm test -- src/features/catalog/catalogAssets.test.ts
```

Expected: backend FAIL because count is `0`; frontend FAIL because all 12 files are absent.

- [ ] **Step 3: Audit the fixed species matrix before writing SQL**

Use this accepted-name matrix as the review starting point; verify each accepted scientific name and family/genus against at least one current taxonomic database linked from the corresponding Commons taxon page or FishBase entry:

| Order | Chinese | Scientific name | Required alias used by tests |
| ---: | --- | --- | --- |
| 1 | 鲫 | `Carassius auratus` | 鲫鱼 |
| 2 | 鲤 | `Cyprinus carpio` | 鲤鱼 |
| 3 | 草鱼 | `Ctenopharyngodon idella` | 鲩鱼 |
| 4 | 青鱼 | `Mylopharyngodon piceus` | 螺蛳青 |
| 5 | 鲢 | `Hypophthalmichthys molitrix` | 白鲢 |
| 6 | 鳙 | `Hypophthalmichthys nobilis` | 胖头鱼 |
| 7 | 乌鳢 | `Channa argus` | 黑鱼 |
| 8 | 鳜 | `Siniperca chuatsi` | 桂花鱼 |
| 9 | 黄颡鱼 | `Tachysurus fulvidraco` | 黄辣丁 |
| 10 | 团头鲂 | `Megalobrama amblycephala` | 武昌鱼 |
| 11 | 翘嘴鲌 | `Culter alburnus` | 白刁 |
| 12 | 泥鳅 | `Misgurnus anguillicaudatus` | 鳅 |

If a current authoritative source treats one listed scientific name as a synonym, record both accepted and synonymous names in the provenance document, use the accepted name in `scientific_name`, preserve the design-approved slug, and add the synonym as an alias searchable by the repository.

- [ ] **Step 4: Audit one image file page per species**

Start from these exact Commons file-page candidates; open each original page and record the displayed author, original source, license name, license URL, and whether changes/cropping are made:

| Slug | Candidate Commons file page |
| --- | --- |
| `carassius-auratus` | `https://commons.wikimedia.org/wiki/File:Carassius_auratus.jpg` |
| `cyprinus-carpio` | `https://commons.wikimedia.org/wiki/File:Cyprinus_carpio.jpeg` |
| `ctenopharyngodon-idella` | `https://commons.wikimedia.org/wiki/File:Ctenopharyngodon_idella.jpg` |
| `mylopharyngodon-piceus` | `https://commons.wikimedia.org/wiki/File:Mylopharyngodon_piceus.jpg` |
| `hypophthalmichthys-molitrix` | `https://commons.wikimedia.org/wiki/File:Hypophthalmichthys_molitrix_02.jpg` |
| `hypophthalmichthys-nobilis` | `https://commons.wikimedia.org/wiki/File:Hypophthalmichthys-nob.jpg` |
| `channa-argus` | `https://commons.wikimedia.org/wiki/File:Channa_argus_01.jpg` |
| `siniperca-chuatsi` | `https://commons.wikimedia.org/wiki/File:Siniperca_chuatsi.jpg` |
| `tachysurus-fulvidraco` | `https://commons.wikimedia.org/wiki/File:Tachysurus_fulvidraco_from_the_Lake_Khanka.jpg` |
| `megalobrama-amblycephala` | `https://commons.wikimedia.org/wiki/File:Megalobrama_amblycephala_Kasumigaura1.jpg` |
| `culter-alburnus` | `https://commons.wikimedia.org/wiki/File:Culter_alburnus.jpg` |
| `misgurnus-anguillicaudatus` | `https://commons.wikimedia.org/wiki/File:Misgurnus_anguillicaudatus.jpg` |

A candidate is acceptable only if it clearly depicts the intended taxon and the file page states Public Domain, CC0, CC BY, or CC BY-SA. Reject non-commercial/no-derivatives licenses, pages without a clear author/source chain, prepared food images, mislabeled taxa, or images where the fish is not identifiable. A rejected candidate must be replaced by another exact Commons file page and the replacement recorded in the task report before download.

- [ ] **Step 5: Download and normalize the 12 approved assets**

Download from each approved Commons “Original file” link, preserve aspect ratio, and encode as JPEG with no upscaling and a maximum long edge of 1600 px. Save only the 12 exact slug filenames. Do not remove required CC BY-SA metadata from the provenance record when resizing.

Verify:

```bash
find frontend/public/images/fish -type f -name '*.jpg' | sort
file frontend/public/images/fish/*.jpg
du -ch frontend/public/images/fish/*.jpg
```

Expected: exactly 12 JPEG files; no HTML download error files; each file is greater than 10 KB. Keep total catalog assets reasonably below 20 MB.

- [ ] **Step 6: Write the provenance document before the seed migration**

Create one table row per slug with these exact columns:

```markdown
| Slug | 中文名 | Accepted scientific name | Taxonomy/content sources | Local image | Original file page | Author | License | License URL | Changes |
```

Every URL must be direct and reviewable. Below the table, state that descriptive text is an original Chinese summary, identify which source supports taxonomy versus ecology/distribution, and record the date `2026-08-11` for the license audit.

- [ ] **Step 7: Create V4 with 12 complete fish rows**

Use explicit IDs `1` through `12` only inside the seed migration so aliases and habitats are deterministic. Use UTC timestamp `2026-08-11 00:00:00.000000`, the exact slug order above, local paths `/images/fish/{slug}.jpg`, and attribution values copied from the audited file pages.

Each fish needs:

- one original Chinese paragraph for `appearance`;
- one source-backed natural-language `size_description` without invented precision;
- one `habitat_description`, `distribution`, and `description` paragraph;
- the required test alias plus only corroborated regional aliases;
- at least two appropriate structured habitat codes;
- image alt text in the form `“{中文名}（{科学学名}）”`;
- `display_order` matching IDs 1–12.

Use three explicit multi-row `INSERT` statements: `fish_species`, then `fish_aliases`, then `fish_habitats`. Do not disable foreign keys and do not use `INSERT IGNORE` or upsert semantics; Flyway must fail on duplicate or incomplete data.

- [ ] **Step 8: Run seed, asset, repository, and build verification**

Run:

```bash
cd backend
./mvnw -Dtest=CatalogDatabaseMigrationTest,JpaFishRepositoryAdapterTest test
cd ../frontend
npm test -- src/features/catalog/catalogAssets.test.ts
npm run build
```

Expected: exact 12-row assertions PASS; all images exist; Vite copies them into `dist/images/fish`; repository fixture cleanup keeps adapter tests deterministic.

- [ ] **Step 9: Commit Task 4**

```bash
git add backend/src/main/resources/db/migration/V4__seed_fish_catalog.sql \
  backend/src/test/java/com/fishbook/catalog/persistence/CatalogDatabaseMigrationTest.java \
  frontend/public/images/fish \
  frontend/src/features/catalog/catalogAssets.test.ts \
  docs/data-sources/fish-catalog-attribution.md
git commit -m "feat: seed audited freshwater fish catalog"
```

### Task 5: Public Catalog API, Error Contract, and Security Boundary

**Files:**
- Create: `backend/src/main/java/com/fishbook/catalog/web/FishCatalogController.java`
- Create: `backend/src/main/java/com/fishbook/catalog/web/dto/FishPageResponse.java`
- Create: `backend/src/main/java/com/fishbook/catalog/web/dto/FishSummaryResponse.java`
- Create: `backend/src/main/java/com/fishbook/catalog/web/dto/FishDetailResponse.java`
- Create: `backend/src/main/java/com/fishbook/catalog/web/dto/FishFilterOptionsResponse.java`
- Create: `backend/src/main/java/com/fishbook/catalog/web/dto/HabitatOptionResponse.java`
- Create: `backend/src/main/java/com/fishbook/catalog/web/dto/ImageAttributionResponse.java`
- Modify: `backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java`
- Create: `backend/src/test/java/com/fishbook/catalog/web/FishCatalogApiIntegrationTest.java`
- Create: `backend/src/test/java/com/fishbook/catalog/web/CatalogAuthorizationTest.java`

**Interfaces:**
- Consumes: Task 3 service and Task 4 seed rows.
- Produces: anonymous `GET /api/v1/fish`, `/api/v1/fish/filters`, `/api/v1/fish/{slug}` contracts for the frontend.

- [ ] **Step 1: Write real-MySQL MockMvc contract tests**

Use `@SpringBootTest`, `@AutoConfigureMockMvc`, and `@Import(MySqlTestConfiguration.class)`. Do not mock the service or repository. Assert:

```java
mvc.perform(get("/api/v1/fish"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(12))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(12))
        .andExpect(jsonPath("$.totalItems").value(12));

mvc.perform(get("/api/v1/fish").param("q", "黑鱼"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].slug").value("channa-argus"));

mvc.perform(get("/api/v1/fish/channa-argus"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commonNameZh").value("乌鳢"))
        .andExpect(jsonPath("$.image.sourceUrl").isNotEmpty())
        .andExpect(jsonPath("$.image.licenseUrl").isNotEmpty());
```

Add the exact positive combination/filter assertions:

```java
mvc.perform(get("/api/v1/fish").param("q", "cHaNnA ArGuS"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].slug").value("channa-argus"));
mvc.perform(get("/api/v1/fish")
                .param("family", "鳢科").param("habitat", "LAKE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].slug").value(hasItem("channa-argus")));
mvc.perform(get("/api/v1/fish/filters"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.families").isArray())
        .andExpect(jsonPath("$.habitats.length()").value(5));
```

Add one test method that performs each invalid request and expects the exact safe code:

```java
mvc.perform(get("/api/v1/fish").param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
mvc.perform(get("/api/v1/fish").param("size", "12"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));
mvc.perform(get("/api/v1/fish").param("q", "鱼".repeat(101)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));
mvc.perform(get("/api/v1/fish").param("habitat", "SEA"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));
mvc.perform(get("/api/v1/fish/missing-fish"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("FISH_NOT_FOUND"));
mvc.perform(get("/api/v1/fish/Bad_Slug"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));
```

- [ ] **Step 2: Write authorization tests before changing security**

```java
@Test
void anonymousCatalogReadsArePublic() throws Exception {
    mvc.perform(get("/api/v1/fish")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/fish/filters")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/fish/channa-argus")).andExpect(status().isOk());
}

@Test
void catalogWritesRemainDeniedWithValidCsrf() throws Exception {
    mvc.perform(post("/api/v1/fish").with(csrf()).contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
}
```

Repeat the denied assertion for PUT, PATCH, and DELETE on a catalog path.

- [ ] **Step 3: Run both test classes to prove RED**

Run:

```bash
./mvnw -Dtest=FishCatalogApiIntegrationTest,CatalogAuthorizationTest test
```

Expected: GET requests return `403 ACCESS_DENIED` because no controller/path permission exists.

- [ ] **Step 4: Add explicit Web response records and controller mappings**

Controller signature:

```java
@RestController
@RequestMapping("/api/v1/fish")
public class FishCatalogController {
    @GetMapping
    FishPageResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String family,
            @RequestParam(required = false) String habitat,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return FishPageResponse.from(service.search(
                FishCatalogQuery.from(q, family, habitat, page, size)));
    }

    @GetMapping("/filters")
    FishFilterOptionsResponse filters() {
        return FishFilterOptionsResponse.from(service.getFilterOptions());
    }

    @GetMapping("/{slug}")
    FishDetailResponse detail(@PathVariable String slug) {
        return FishDetailResponse.from(service.getBySlug(slug));
    }
}
```

Each response record has a static `from(...)` mapper for its matching application view; nested lists map with `.stream().map(...).toList()`. Response records contain only the public fields shown in Task 3. Do not serialize application/domain records implicitly.

- [ ] **Step 5: Map catalog exceptions in the common handler**

```java
@ExceptionHandler(InvalidCatalogQueryException.class)
ResponseEntity<ApiErrorResponse> handleInvalidCatalogQuery(
        InvalidCatalogQueryException exception, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, exception.code(),
            "Catalog query is invalid", List.of(), request);
}

@ExceptionHandler(FishNotFoundException.class)
ResponseEntity<ApiErrorResponse> handleFishNotFound(
        FishNotFoundException exception, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, exception.code(),
            "Fish was not found", List.of(), request);
}
```

The public message must not echo a malformed query or slug.

- [ ] **Step 6: Add method-scoped public security rules**

Before existing authentication-required matchers and `.anyRequest().denyAll()`, add:

```java
.requestMatchers(HttpMethod.GET, "/api/v1/fish", "/api/v1/fish/**")
.permitAll()
```

Import `org.springframework.http.HttpMethod`. Do not add the catalog to the existing string-only permit list, because that would also permit unsafe methods.

- [ ] **Step 7: Run focused API/security GREEN**

Run:

```bash
./mvnw -Dtest=FishCatalogApiIntegrationTest,CatalogAuthorizationTest,IdentityAuthorizationTest,IdentityErrorContractTest test
```

Expected: catalog contract tests PASS; identity authorization/error contracts remain PASS.

- [ ] **Step 8: Run the full backend suite and commit**

Run `./mvnw test`; expected: all tests PASS on MySQL 8.4.

Then:

```bash
git add backend/src/main/java/com/fishbook/catalog/web \
  backend/src/main/java/com/fishbook/common/error/GlobalExceptionHandler.java \
  backend/src/main/java/com/fishbook/identity/security/SecurityConfig.java \
  backend/src/test/java/com/fishbook/catalog/web
git commit -m "feat: expose public fish catalog API"
```

### Task 6: Typed Frontend API and URL Query State

**Files:**
- Create: `frontend/src/features/catalog/model/types.ts`
- Create: `frontend/src/features/catalog/model/catalogSearchParams.ts`
- Create: `frontend/src/features/catalog/model/catalogSearchParams.test.ts`
- Create: `frontend/src/features/catalog/api/catalogApi.ts`
- Create: `frontend/src/features/catalog/api/catalogApi.test.ts`

**Interfaces:**
- Consumes: Task 5 JSON contracts and existing `apiFetch`.
- Produces: typed fetch functions, stable query keys, and URL parse/serialize helpers for pages.

- [ ] **Step 1: Write URL state tests first**

```ts
expect(parseCatalogSearchParams(new URLSearchParams(
  'q=%20%E9%BB%91%E9%B1%BC%20&family=%20%E9%B3%A2%E7%A7%91&habitat=lake&page=2',
))).toEqual({ q: '黑鱼', family: '鳢科', habitat: 'LAKE', page: 2 });

expect(parseCatalogSearchParams(new URLSearchParams('habitat=sea&page=-1')))
  .toEqual({ q: '', family: '', habitat: '', page: 0 });

expect(toCatalogSearchParams({ q: '', family: '', habitat: '', page: 0 }).toString())
  .toBe('');
```

Assert the non-empty serialization order and page omission explicitly:

```ts
expect(toCatalogSearchParams({
  q: '黑鱼', family: '鳢科', habitat: 'LAKE', page: 2,
}).toString()).toBe(
  'q=%E9%BB%91%E9%B1%BC&family=%E9%B3%A2%E7%A7%91&habitat=LAKE&page=2',
);
expect(toCatalogSearchParams({
  q: '黑鱼', family: '', habitat: '', page: 0,
}).toString()).toBe('q=%E9%BB%91%E9%B1%BC');
```

- [ ] **Step 2: Write API adapter tests first**

Mock `global.fetch`. Assert `fetchFishPage({q:'黑鱼', family:'', habitat:'LAKE', page:1})` requests:

```text
/api/v1/fish?q=%E9%BB%91%E9%B1%BC&habitat=LAKE&page=1
```

Assert filters use `/api/v1/fish/filters`, details URL-encode the slug, and query keys differ when any filter changes:

```ts
await fetchFishFilterOptions();
expect(fetchMock).toHaveBeenLastCalledWith(
  '/api/v1/fish/filters', expect.objectContaining({ credentials: 'include' }),
);
await fetchFishDetail('fish slug');
expect(fetchMock).toHaveBeenLastCalledWith(
  '/api/v1/fish/fish%20slug', expect.objectContaining({ credentials: 'include' }),
);
expect(fishListQueryKey({ q: '', family: '', habitat: '', page: 0 }))
  .not.toEqual(fishListQueryKey({ q: '鲤', family: '', habitat: '', page: 0 }));
```

- [ ] **Step 3: Run both tests to prove RED**

Run:

```bash
npm test -- src/features/catalog/model/catalogSearchParams.test.ts \
  src/features/catalog/api/catalogApi.test.ts
```

Expected: module-resolution FAIL because catalog model/API files do not exist.

- [ ] **Step 4: Add exact TypeScript contracts**

```ts
export type HabitatCode = 'RIVER' | 'LAKE' | 'RESERVOIR' | 'POND' | 'STREAM';
export type HabitatOption = { code: HabitatCode; labelZh: string };
export type FishImageAttribution = {
  path: string;
  altText: string;
  sourceUrl: string;
  author: string;
  licenseName: string;
  licenseUrl: string;
};
export type FishSummary = {
  slug: string;
  commonNameZh: string;
  scientificName: string;
  familyNameZh: string;
  aliases: string[];
  habitats: HabitatOption[];
  imagePath: string;
  imageAltText: string;
};
export type FishPage = {
  items: FishSummary[];
  page: number;
  size: 12;
  totalItems: number;
  totalPages: number;
};
export type FishDetail = {
  slug: string;
  commonNameZh: string;
  scientificName: string;
  familyNameZh: string;
  familyScientificName: string;
  genusNameZh: string;
  genusScientificName: string;
  aliases: string[];
  habitats: HabitatOption[];
  appearance: string;
  sizeDescription: string;
  habitatDescription: string;
  distribution: string;
  description: string;
  image: FishImageAttribution;
};
export type FishFilterOptions = {
  families: string[];
  habitats: HabitatOption[];
};
export type CatalogFilters = {
  q: string;
  family: string;
  habitat: HabitatCode | '';
  page: number;
};
```

- [ ] **Step 5: Implement deterministic URL helpers and API calls**

```ts
export const fishListQueryKey = (filters: CatalogFilters) => [
  'fish-catalog', 'list', filters.q, filters.family, filters.habitat, filters.page,
] as const;

export async function fetchFishPage(filters: CatalogFilters): Promise<FishPage> {
  const params = toCatalogSearchParams(filters);
  return apiFetch<FishPage>(`/api/v1/fish${params.size ? `?${params}` : ''}`);
}

export const fishDetailQueryKey = (slug: string) =>
  ['fish-catalog', 'detail', slug] as const;

export const fishFilterOptionsQueryKey = ['fish-catalog', 'filters'] as const;

export function fetchFishDetail(slug: string): Promise<FishDetail> {
  return apiFetch<FishDetail>(`/api/v1/fish/${encodeURIComponent(slug)}`);
}

export function fetchFishFilterOptions(): Promise<FishFilterOptions> {
  return apiFetch<FishFilterOptions>('/api/v1/fish/filters');
}
```

`parseCatalogSearchParams` trims values, uppercases only a known habitat code, defaults invalid/negative/non-integer pages to 0, and never introduces `size`.

- [ ] **Step 6: Run focused and shared HTTP tests**

Run:

```bash
npm test -- src/features/catalog/model/catalogSearchParams.test.ts \
  src/features/catalog/api/catalogApi.test.ts \
  src/shared/api/httpClient.test.ts
```

Expected: PASS; public GET requests do not trigger CSRF bootstrap and still use same-origin credentials.

- [ ] **Step 7: Commit Task 6**

```bash
git add frontend/src/features/catalog/model frontend/src/features/catalog/api
git commit -m "feat: add typed fish catalog client"
```

### Task 7: URL-Driven Catalog List Page

**Files:**
- Create: `frontend/src/features/catalog/components/CatalogSearchForm.tsx`
- Create: `frontend/src/features/catalog/components/CatalogFilters.tsx`
- Create: `frontend/src/features/catalog/components/FishCard.tsx`
- Create: `frontend/src/features/catalog/components/CatalogPagination.tsx`
- Create: `frontend/src/features/catalog/pages/FishCatalogPage.tsx`
- Create: `frontend/src/features/catalog/pages/FishCatalogPage.module.css`
- Create: `frontend/src/features/catalog/pages/FishCatalogPage.test.tsx`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/app/App.test.tsx`

**Interfaces:**
- Consumes: Task 6 query helpers/API and existing React Query/router providers.
- Produces: anonymous `/` catalog UI and reusable cards/pagination.

- [ ] **Step 1: Write list-page behavior tests with mocked catalog API**

Define deterministic fixtures and a real in-memory router helper in the test file:

```ts
const channaSummary: FishSummary = {
  slug: 'channa-argus',
  commonNameZh: '乌鳢',
  scientificName: 'Channa argus',
  familyNameZh: '鳢科',
  aliases: ['黑鱼'],
  habitats: [{ code: 'LAKE', labelZh: '湖泊' }],
  imagePath: '/images/fish/channa-argus.jpg',
  imageAltText: '乌鳢（Channa argus）',
};

const pageWith12Fish: FishPage = {
  items: [
    channaSummary,
    ...Array.from({ length: 11 }, (_, index) => ({
      ...channaSummary,
      slug: `fixture-fish-${index + 1}`,
      commonNameZh: `测试鱼${index + 1}`,
    })),
  ],
  page: 0,
  size: 12,
  totalItems: 12,
  totalPages: 1,
};

const filterOptions: FishFilterOptions = {
  families: ['鳢科', '鲤科'],
  habitats: [
    { code: 'RIVER', labelZh: '江河' },
    { code: 'LAKE', labelZh: '湖泊' },
  ],
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function renderCatalog(initialEntry: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const user = userEvent.setup();
  return {
    user,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path="/" element={<><FishCatalogPage /><LocationProbe /></>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}
```

Reset both API mocks before each test and default them to `pageWith12Fish` and `filterOptions`. Then cover these observable cases:

```ts
test('renders cards and hides pagination for one page', async () => {
  fetchFishPageMock.mockResolvedValue(pageWith12Fish);
  fetchFishFiltersMock.mockResolvedValue(filterOptions);
  renderCatalog('/');
  expect(await screen.findByRole('link', { name: /查看乌鳢详情/ })).toBeInTheDocument();
  expect(screen.queryByRole('navigation', { name: '图鉴分页' })).not.toBeInTheDocument();
});

test('submits a trimmed search and resets page', async () => {
  const { user } = renderCatalog('/?page=2');
  await user.type(screen.getByRole('searchbox', { name: '搜索鱼类' }), '  黑鱼  ');
  await user.click(screen.getByRole('button', { name: '搜索' }));
  expect(screen.getByTestId('location')).toHaveTextContent('/?q=黑鱼');
});

test('changing habitat keeps other filters and resets page', async () => {
  const { user } = renderCatalog('/?q=鲤&family=鲤科&page=2');
  await user.selectOptions(screen.getByLabelText('栖息环境'), 'LAKE');
  expect(screen.getByTestId('location')).toHaveTextContent(
    '/?q=鲤&family=鲤科&habitat=LAKE',
  );
});
```

Use this state matrix for the remaining tests; each row is a separate test:

| Setup/action | Exact assertion |
| --- | --- |
| unresolved list promise | `role="status"` contains `正在加载鱼类…` |
| page with `items: []` at `/?q=missing` | heading `没有找到匹配的鱼类`; clicking `清除筛选` changes location to `/` |
| list rejects with API 500 containing SQL text | status contains only `加载鱼类失败，请稍后重试`; SQL text is absent; `重试` calls the mock again |
| page 0 of 2 | pagination label is `图鉴分页`; previous disabled; next enabled; clicking next writes `page=1` |
| fire `error` on 乌鳢 image | `<img>` is removed and `role="img"` with label `乌鳢（Channa argus）` appears |
| rerender after location changes from `?q=黑鱼` to `?q=鲤` | searchbox value becomes `鲤` |

- [ ] **Step 2: Run the page test to prove RED**

Run `npm test -- src/features/catalog/pages/FishCatalogPage.test.tsx`.

Expected: module-resolution FAIL because the page/components are absent.

- [ ] **Step 3: Implement focused presentational components**

Contracts:

```ts
type CatalogSearchFormProps = {
  submittedQuery: string;
  onSubmit: (query: string) => void;
};

type CatalogFiltersProps = {
  families: string[];
  habitats: HabitatOption[];
  family: string;
  habitat: HabitatCode | '';
  onFamilyChange: (family: string) => void;
  onHabitatChange: (habitat: HabitatCode | '') => void;
  onClear: () => void;
};

type CatalogPaginationProps = {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
};

type FishCardProps = {
  fish: FishSummary;
  from: string;
};
```

`FishCard` wraps content in `<article>` and links to `/fish/${fish.slug}` with accessible name `查看{commonNameZh}详情` and `state={{ from }}`. Keep alt text on a real `<img>` until failure; after failure render a `role="img"` fallback labelled with the same alt text.

- [ ] **Step 4: Implement URL-driven page orchestration**

`FishCatalogPage` uses `useLocation()` and `useSearchParams()` and calls `parseCatalogSearchParams(searchParams)` on every render. All update handlers construct a complete `CatalogFilters` value and call `setSearchParams(toCatalogSearchParams(next))`; they do not maintain a second committed filter object in React state. Pass `${location.pathname}${location.search}` to every `FishCard` as `from`.

Use two queries:

```ts
const fishQuery = useQuery({
  queryKey: fishListQueryKey(filters),
  queryFn: () => fetchFishPage(filters),
});
const filterQuery = useQuery({
  queryKey: fishFilterOptionsQueryKey,
  queryFn: fetchFishFilterOptions,
});
```

The search form may hold only the unsubmitted draft string and must resync when `submittedQuery` changes due to browser navigation.

- [ ] **Step 5: Make the catalog the home page and preserve product identity**

`App` becomes:

```tsx
export function App() {
  return <FishCatalogPage />;
}
```

Update `App.test.tsx` to render with a `QueryClientProvider` and `MemoryRouter`, mock the two catalog API calls, and continue asserting the `FishBook` heading. The page header contains links to 首页、登录、注册、个人资料; it does not attempt to infer authentication state.

- [ ] **Step 6: Add responsive and accessible CSS**

Use CSS Modules only. Define a centered max-width page, visible focus states, a filter grid, card grid using `repeat(auto-fit, minmax(15rem, 1fr))`, fixed image aspect ratio with `object-fit: cover`, readable scientific-name italics, and a mobile breakpoint. Do not encode fish data or status in CSS.

- [ ] **Step 7: Run focused list tests, App test, lint, and build**

Run:

```bash
npm test -- src/features/catalog/pages/FishCatalogPage.test.tsx src/app/App.test.tsx
npm run lint
npm run build
```

Expected: all PASS; TypeScript accepts exact API types; no accessibility query ambiguity.

- [ ] **Step 8: Commit Task 7**

```bash
git add frontend/src/features/catalog/components \
  frontend/src/features/catalog/pages/FishCatalogPage.tsx \
  frontend/src/features/catalog/pages/FishCatalogPage.module.css \
  frontend/src/features/catalog/pages/FishCatalogPage.test.tsx \
  frontend/src/app/App.tsx frontend/src/app/App.test.tsx
git commit -m "feat: add fish catalog browsing page"
```

### Task 8: Fish Detail Page and Deep Link

**Files:**
- Create: `frontend/src/features/catalog/pages/FishDetailPage.tsx`
- Create: `frontend/src/features/catalog/pages/FishDetailPage.module.css`
- Create: `frontend/src/features/catalog/pages/FishDetailPage.test.tsx`
- Modify: `frontend/src/features/catalog/components/FishCard.tsx`
- Modify: `frontend/src/app/router.tsx`

**Interfaces:**
- Consumes: Task 6 `fetchFishDetail`/query key and Task 4 image attribution data.
- Produces: refresh-safe `/fish/:slug` route.

- [ ] **Step 1: Write detail-page tests before the route**

Define the exact detail fixture and router helper:

```ts
const channaArgusDetail: FishDetail = {
  slug: 'channa-argus',
  commonNameZh: '乌鳢',
  scientificName: 'Channa argus',
  familyNameZh: '鳢科',
  familyScientificName: 'Channidae',
  genusNameZh: '鳢属',
  genusScientificName: 'Channa',
  aliases: ['黑鱼', '生鱼'],
  habitats: [
    { code: 'LAKE', labelZh: '湖泊' },
    { code: 'POND', labelZh: '池塘' },
  ],
  appearance: '身体延长，头部宽扁，体侧有深色斑纹。',
  sizeDescription: '常见个体为中型淡水鱼。',
  habitatDescription: '常见于水草较多的静水或缓流水域。',
  distribution: '分布于中国多地淡水水系。',
  description: '乌鳢是适应力较强的淡水鱼。',
  image: {
    path: '/images/fish/channa-argus.jpg',
    altText: '乌鳢（Channa argus）',
    sourceUrl: 'https://commons.wikimedia.org/wiki/File:Channa_argus_01.jpg',
    author: 'Σ64',
    licenseName: 'CC BY 4.0',
    licenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
  },
};

function renderDetail(
  initialEntry: string | { pathname: string; state?: { from: string } },
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/fish/:slug" element={<FishDetailPage />} />
          <Route path="/" element={<h1>图鉴</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
```

Then cover:

```ts
test('renders classification, content, and visible image attribution', async () => {
  fetchFishDetailMock.mockResolvedValue(channaArgusDetail);
  renderDetail('/fish/channa-argus');
  expect(await screen.findByRole('heading', { name: '乌鳢' })).toBeInTheDocument();
  expect(screen.getByText('Channa argus')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /图片来源/ })).toHaveAttribute(
    'href', channaArgusDetail.image.sourceUrl,
  );
  expect(screen.getByRole('link', { name: /许可证/ })).toHaveAttribute(
    'href', channaArgusDetail.image.licenseUrl,
  );
});

test('shows a dedicated not-found state for a 404', async () => {
  fetchFishDetailMock.mockRejectedValue(new ApiError(404, {
    code: 'FISH_NOT_FOUND',
    message: 'Fish was not found',
    fieldErrors: [],
    requestId: 'test-request',
  }));
  renderDetail('/fish/missing-fish');
  expect(await screen.findByRole('heading', { name: '没有找到这种鱼' })).toBeInTheDocument();
});
```

Use this state matrix for the remaining tests; each row is a separate test:

| Setup/action | Exact assertion |
| --- | --- |
| unresolved detail promise | `role="status"` contains `正在加载鱼类资料…` |
| API 500 containing database text | only `加载鱼类资料失败，请稍后重试` appears; database text is absent; `重试` refetches |
| successful detail | image `src` is `/images/fish/channa-argus.jpg`; both aliases and both habitat labels render |
| fire `error` on detail image | labelled fallback replaces the `<img>` |
| `renderDetail({ pathname: '/fish/channa-argus', state: { from: '/?q=黑鱼&habitat=LAKE' } })` | `返回图鉴` has that exact href |
| render direct URL without state | `返回图鉴` has `href="/"` |

- [ ] **Step 2: Run the focused detail test to prove RED**

Run `npm test -- src/features/catalog/pages/FishDetailPage.test.tsx`.

Expected: module-resolution FAIL because the detail page is absent.

- [ ] **Step 3: Implement the slug query and explicit states**

Read `slug` with `useParams()` and the prior URL with `useLocation()`. If slug is absent, render the not-found state without sending a request. Treat `location.state` as untrusted: use `state.from` only when it is a string beginning with `/?` or exactly `/`; otherwise fall back to `/`. Then query:

```ts
const detail = useQuery({
  queryKey: fishDetailQueryKey(slug),
  queryFn: () => fetchFishDetail(slug),
});
```

Treat only `ApiError` status 404 as the dedicated missing state; all other errors show `加载鱼类资料失败，请稍后重试` and a retry button. Never render backend error messages.

Render image attribution next to the image with author text and external links using `target="_blank" rel="noreferrer"`.

- [ ] **Step 4: Add the deep-link route**

```tsx
{
  path: '/fish/:slug',
  element: <FishDetailPage />,
}
```

When creating the card link in Task 7, pass `{ state: { from: currentCatalogLocation } }`; do not depend on that state for direct links or refresh.

- [ ] **Step 5: Add readable detail CSS**

Use a two-column image/content layout above tablet width and one column below. Keep prose line length near 70 characters, italicize scientific names, preserve heading hierarchy, and style attribution as visible text rather than a tooltip.

- [ ] **Step 6: Run detail, list, router, lint, and build verification**

Run:

```bash
npm test -- src/features/catalog/pages/FishDetailPage.test.tsx \
  src/features/catalog/pages/FishCatalogPage.test.tsx src/app/App.test.tsx
npm run lint
npm run build
```

Expected: PASS; direct `/fish/channa-argus` is present in Vite's SPA build and is handled by the existing Nginx `try_files` fallback.

- [ ] **Step 7: Commit Task 8**

```bash
git add frontend/src/features/catalog/pages/FishDetailPage.tsx \
  frontend/src/features/catalog/pages/FishDetailPage.module.css \
  frontend/src/features/catalog/pages/FishDetailPage.test.tsx \
  frontend/src/features/catalog/components/FishCard.tsx \
  frontend/src/app/router.tsx
git commit -m "feat: add fish catalog detail page"
```

### Task 9: Real Browser Flow, Runbook, and Full Regression

**Files:**
- Create: `e2e/tests/catalog-flow.spec.ts`
- Modify: `docs/runbooks/local-development.md`
- Verify without modification unless a real gap is found: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: Tasks 1–8 and the existing Docker/Nginx/CI pipeline.
- Produces: an executable completion gate for the entire catalog slice.

- [ ] **Step 1: Write the Playwright catalog flow before rebuilding images**

```ts
test('browses, searches, filters, and deep-links through the public catalog', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'FishBook' })).toBeVisible();
  await expect(page.getByRole('article')).toHaveCount(12);

  await page.getByRole('searchbox', { name: '搜索鱼类' }).fill('黑鱼');
  await page.getByRole('button', { name: '搜索' }).click();
  await expect(page).toHaveURL(/q=%E9%BB%91%E9%B1%BC/);
  await expect(page.getByRole('link', { name: '查看乌鳢详情' })).toBeVisible();

  await page.getByLabel('栖息环境').selectOption('LAKE');
  await expect(page).toHaveURL(/habitat=LAKE/);
  await page.getByRole('link', { name: '查看乌鳢详情' }).click();
  await expect(page).toHaveURL(/\/fish\/channa-argus$/);
  await expect(page.getByRole('heading', { name: '乌鳢' })).toBeVisible();
  await expect(page.getByRole('link', { name: /许可证/ })).toBeVisible();

  const imageResponse = await page.request.get('/images/fish/channa-argus.jpg');
  expect(imageResponse.status()).toBe(200);
  expect(imageResponse.headers()['content-type']).toContain('image/jpeg');

  await page.reload();
  await expect(page.getByRole('heading', { name: '乌鳢' })).toBeVisible();
  await page.getByRole('link', { name: '返回图鉴' }).click();
  await expect(page).toHaveURL(/q=%E9%BB%91%E9%B1%BC/);
});
```

- [ ] **Step 2: Run E2E before rebuilding to prove deployment RED**

Run against the currently running pre-catalog stack:

```bash
cd e2e
npm test -- catalog-flow.spec.ts
```

Expected: FAIL because the deployed home page has no catalog cards/search controls.

- [ ] **Step 3: Rebuild the full stack without deleting volumes**

From repository root:

```bash
docker compose -f compose.yaml -f compose.full.yaml config --quiet
docker compose -f compose.yaml -f compose.full.yaml up -d --build
docker compose -f compose.yaml -f compose.full.yaml ps
```

Expected: MySQL, MinIO, and backend are healthy; frontend is running on host port 8080; Flyway applies V3 and V4 once.

- [ ] **Step 4: Run the catalog and identity browser flows**

```bash
cd e2e
npm test -- catalog-flow.spec.ts auth-flow.spec.ts
```

Expected: both catalog and existing identity flows PASS. If a browser test fails, use `superpowers:systematic-debugging` before changing product code or weakening assertions.

- [ ] **Step 5: Add runbook smoke checks**

Document:

```bash
curl -fsS 'http://localhost:8080/api/v1/fish?page=0'
curl -fsS 'http://localhost:8080/api/v1/fish?q=%E9%BB%91%E9%B1%BC'
curl -fsS 'http://localhost:8080/api/v1/fish/channa-argus'
curl -I 'http://localhost:8080/images/fish/channa-argus.jpg'
```

Add links to `/`, `/fish/channa-argus`, and `docs/data-sources/fish-catalog-attribution.md`. State that catalog writes and image uploads are intentionally unavailable.

- [ ] **Step 6: Run fresh full verification**

Backend:

```bash
cd backend
./mvnw test
```

Frontend under Node 24.18.0:

```bash
cd frontend
node --version
npm test
npm run lint
npm run build
```

E2E and runtime:

```bash
cd e2e
npm test
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8080/api/v1/fish/filters
```

Expected: every command exits `0`; Node reports `v24.18.0`; all backend, frontend, identity E2E, and catalog E2E tests pass; health/readiness report `UP`.

- [ ] **Step 7: Review CI coverage and repository hygiene**

Confirm the existing workflow already runs `./mvnw test`, frontend test/lint/build, full Compose, and all Playwright tests. Do not edit `.github/workflows/ci.yml` when discovery already covers the new files.

Run:

```bash
git diff --check
git status --short
git ls-files frontend/public/images/fish docs/data-sources/fish-catalog-attribution.md
```

Expected: no whitespace errors; exactly 12 image paths are tracked; no `dist`, `node_modules`, test artifacts, `.env`, or local secrets are staged.

- [ ] **Step 8: Commit Task 9**

```bash
git add e2e/tests/catalog-flow.spec.ts docs/runbooks/local-development.md
git commit -m "test: verify fish catalog end to end"
```

- [ ] **Step 9: Request final code review before integration**

Use `superpowers:requesting-code-review` against the complete feature branch. Resolve verified findings with `superpowers:receiving-code-review`, rerun the full verification in Step 6, and only then use `superpowers:finishing-a-development-branch` to offer merge/push/PR choices.
