package com.fishbook.catalog.persistence;

import com.fishbook.catalog.domain.FishPage;
import com.fishbook.catalog.domain.FishManagementSearchCriteria;
import com.fishbook.catalog.domain.FishSearchCriteria;
import com.fishbook.catalog.domain.FishSpecies;
import com.fishbook.catalog.domain.FishSpeciesContent;
import com.fishbook.catalog.domain.HabitatType;
import com.fishbook.catalog.domain.ImageAttribution;
import com.fishbook.catalog.domain.PublicationStatus;
import com.fishbook.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.EnumSet;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({MySqlTestConfiguration.class, JpaFishRepositoryAdapter.class})
class JpaFishRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-08-12T00:00:00Z");

    @Autowired
    private JpaFishRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager entityManager;

    private long unpublishedId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM fish_habitats");
        jdbcTemplate.update("DELETE FROM fish_aliases");
        jdbcTemplate.update("DELETE FROM fish_species");
        insertFish(1L, "cyprinus-carpio", "鲤", "Cyprinus carpio", "鲤科", "鲤鱼", 1,
                HabitatType.RIVER, HabitatType.LAKE);
        insertFish(2L, "channa-argus", "乌鳢", "Channa argus", "鳢科", "黑鱼", 2,
                HabitatType.LAKE, HabitatType.POND);
        insertFish(3L, "siniperca-chuatsi", "鳜", "Siniperca chuatsi", "鳜科", "桂花鱼", 3,
                HabitatType.RIVER, HabitatType.RESERVOIR);
        insertFish(4L, "draft-fish", "草稿鱼", "Draftus fish", "草稿科", "草稿别名", 4,
                PublicationStatus.DRAFT, HabitatType.STREAM);
        unpublishedId = 5L;
        insertFish(unpublishedId, "unpublished-fish", "下架鱼", "Unpublishedus fish", "下架科", "下架别名", 5,
                PublicationStatus.UNPUBLISHED, HabitatType.STREAM);
        entityManager.clear();
    }

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

    @Test
    void savesPublicationStateChangeWhileRetainingExistingAliasesAndHabitats() {
        FishSpecies saved = adapter.save(FishSpecies.createDraft("published-fish", validContent(), NOW));

        FishSpecies published = adapter.save(saved.publish(LATER));

        assertThat(published.status()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(published.aliases()).containsExactly("测试别名");
        assertThat(published.habitats()).containsExactly(HabitatType.RIVER);
    }

    @Test
    void replacesAliasesThatAreEquivalentUnderMySqlCollation() {
        FishSpecies saved = adapter.save(FishSpecies.createDraft(
                "collation-fish", content(List.of("Black Fish"), EnumSet.of(HabitatType.RIVER)), NOW));

        FishSpecies edited = adapter.save(saved.edit(
                content(List.of("black fish"), EnumSet.of(HabitatType.RIVER)), LATER));

        assertThat(edited.aliases()).containsExactly("black fish");
    }

    @Test
    void searchesByCommonName() {
        assertThat(adapter.searchPublished(new FishSearchCriteria("鲤", null, null, 0, 12)).items())
                .extracting(FishSpecies::slug)
                .containsExactly("cyprinus-carpio");
    }

    @Test
    void searchesByAlias() {
        assertThat(adapter.searchPublished(new FishSearchCriteria("黑鱼", null, null, 0, 12)).items())
                .extracting(FishSpecies::slug)
                .containsExactly("channa-argus");
    }

    @Test
    void searchesCaseInsensitivelyByScientificName() {
        assertThat(adapter.searchPublished(new FishSearchCriteria("CHAnNa ARGus", null, null, 0, 12)).items())
                .extracting(FishSpecies::slug)
                .containsExactly("channa-argus");
    }

    @Test
    void filtersByFamilyAndHabitat() {
        assertThat(adapter.searchPublished(new FishSearchCriteria(null, "鳜科", HabitatType.RESERVOIR, 0, 12)).items())
                .extracting(FishSpecies::slug)
                .containsExactly("siniperca-chuatsi");
    }

    @Test
    void findsDetailsBySlug() {
        assertThat(adapter.findPublishedBySlug("channa-argus")).get()
                .extracting(FishSpecies::aliases)
                .asList().contains("黑鱼");
    }

    @Test
    void findsDetailsByIdsInRequestedOrder() {
        assertThat(adapter.findAllByIds(List.of(2L, 1L)))
                .extracting(FishSpecies::slug)
                .containsExactly("channa-argus", "cyprinus-carpio");
        assertThat(adapter.findAllByIds(List.of(2L, 1L)))
                .extracting(FishSpecies::aliases)
                .containsExactly(List.of("黑鱼"), List.of("鲤鱼"));
        assertThat(adapter.findAllByIds(List.of(2L, 1L)))
                .extracting(FishSpecies::habitats)
                .containsExactly(
                        EnumSet.of(HabitatType.LAKE, HabitatType.POND),
                        EnumSet.of(HabitatType.RIVER, HabitatType.LAKE));
    }

    @Test
    void findsDetailsBySlugsInRequestedOrder() {
        assertThat(adapter.findAllPublishedBySlugs(List.of("channa-argus", "cyprinus-carpio")))
                .extracting(FishSpecies::slug)
                .containsExactly("channa-argus", "cyprinus-carpio");
        assertThat(adapter.findAllPublishedBySlugs(List.of("channa-argus", "cyprinus-carpio")))
                .extracting(FishSpecies::aliases)
                .containsExactly(List.of("黑鱼"), List.of("鲤鱼"));
        assertThat(adapter.findAllPublishedBySlugs(List.of("channa-argus", "cyprinus-carpio")))
                .extracting(FishSpecies::habitats)
                .containsExactly(
                        EnumSet.of(HabitatType.LAKE, HabitatType.POND),
                        EnumSet.of(HabitatType.RIVER, HabitatType.LAKE));
    }

    @Test
    void reconstructsHabitatsInEnumDeclarationOrderRegardlessOfInsertionOrder() {
        jdbcTemplate.update("DELETE FROM fish_habitats WHERE fish_species_id = 1");
        for (HabitatType habitat : List.of(
                HabitatType.STREAM,
                HabitatType.POND,
                HabitatType.RESERVOIR,
                HabitatType.LAKE,
                HabitatType.RIVER)) {
            jdbcTemplate.update(
                    "INSERT INTO fish_habitats (fish_species_id, habitat_code) VALUES (?, ?)",
                    1L, habitat.name());
        }
        entityManager.clear();

        assertThat(adapter.findPublishedBySlug("cyprinus-carpio").orElseThrow().habitats())
                .containsExactly(
                        HabitatType.RIVER,
                        HabitatType.LAKE,
                        HabitatType.RESERVOIR,
                        HabitatType.POND,
                        HabitatType.STREAM);
    }

    @Test
    void findsAvailableFamiliesInNaturalOrder() {
        assertThat(adapter.findPublishedAvailableFamilies()).containsExactly("鲤科", "鳜科", "鳢科");
    }

    @Test
    void paginatesWithTotalCountAndStableOrder() {
        jdbcTemplate.update("DELETE FROM fish_habitats");
        jdbcTemplate.update("DELETE FROM fish_aliases");
        jdbcTemplate.update("DELETE FROM fish_species");
        for (int index = 1; index <= 13; index++) {
            insertFish(100L + index, "fixture-fish-" + index, "测试鱼" + index,
                    "Testus fish" + index, "测试科", "别名" + index,
                    index, HabitatType.RIVER);
        }
        entityManager.clear();

        FishPage first = adapter.searchPublished(new FishSearchCriteria(null, null, null, 0, 12));
        FishPage second = adapter.searchPublished(new FishSearchCriteria(null, null, null, 1, 12));

        assertThat(first.items()).hasSize(12);
        assertThat(first.items().getFirst().slug()).isEqualTo("fixture-fish-1");
        assertThat(first.totalItems()).isEqualTo(13);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(second.items()).extracting(FishSpecies::slug)
                .containsExactly("fixture-fish-13");
    }

    private void insertFish(
            long id,
            String slug,
            String commonName,
            String scientificName,
            String familyName,
            String alias,
            int displayOrder,
            HabitatType... habitats) {
        insertFish(id, slug, commonName, scientificName, familyName, alias, displayOrder,
                PublicationStatus.PUBLISHED, habitats);
    }

    private void insertFish(
            long id,
            String slug,
            String commonName,
            String scientificName,
            String familyName,
            String alias,
            int displayOrder,
            PublicationStatus publicationStatus,
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
                    display_order, publication_status, published_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'Testidae', '测试属', 'Testgenus',
                    '外形描述', '体型描述', '栖息环境描述', '分布描述', '综合介绍',
                    ?, ?, 'https://commons.wikimedia.org/wiki/File:Test.jpg',
                    'Test Author', 'CC BY 4.0',
                    'https://creativecommons.org/licenses/by/4.0/',
                    ?, ?, ?, '2026-08-11 00:00:00.000000', '2026-08-11 00:00:00.000000')
                """,
                id, slug, commonName, scientificName, familyName,
                "/images/fish/" + slug + ".jpg",
                commonName + "（" + scientificName + "）",
                displayOrder, publicationStatus.name(),
                publicationStatus == PublicationStatus.DRAFT ? null : NOW);
        jdbcTemplate.update(
                "INSERT INTO fish_aliases (fish_species_id, alias) VALUES (?, ?)",
                id, alias);
        for (HabitatType habitat : habitats) {
            jdbcTemplate.update(
                    "INSERT INTO fish_habitats (fish_species_id, habitat_code) VALUES (?, ?)",
                    id, habitat.name());
        }
    }

    private static FishSpeciesContent validContent() {
        return content(List.of("测试别名"), EnumSet.of(HabitatType.RIVER));
    }

    private static FishSpeciesContent editedContent() {
        return content(List.of("新别名"), EnumSet.of(HabitatType.LAKE));
    }

    private static FishSpeciesContent content(List<String> aliases, EnumSet<HabitatType> habitats) {
        return new FishSpeciesContent(
                "测试鱼", "Testus fish", "测试科", "Testidae", "测试属", "Testgenus",
                aliases, habitats, "外形描述", "体型描述", "栖息环境描述", "分布描述", "综合介绍",
                new ImageAttribution(
                        "/images/fish/test.jpg", "测试鱼", "https://example.test/source",
                        "Test Author", "CC BY 4.0", "https://example.test/license"),
                6);
    }
}
