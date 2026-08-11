package com.fishbook.catalog.persistence;

import com.fishbook.catalog.domain.FishPage;
import com.fishbook.catalog.domain.FishSearchCriteria;
import com.fishbook.catalog.domain.FishSpecies;
import com.fishbook.catalog.domain.HabitatType;
import com.fishbook.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({MySqlTestConfiguration.class, JpaFishRepositoryAdapter.class})
class JpaFishRepositoryAdapterTest {

    @Autowired
    private JpaFishRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager entityManager;

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
        entityManager.clear();
    }

    @Test
    void searchesByCommonName() {
        assertThat(adapter.search(new FishSearchCriteria("鲤", null, null, 0, 12)).items())
                .extracting(FishSpecies::slug)
                .containsExactly("cyprinus-carpio");
    }

    @Test
    void searchesByAlias() {
        assertThat(adapter.search(new FishSearchCriteria("黑鱼", null, null, 0, 12)).items())
                .extracting(FishSpecies::slug)
                .containsExactly("channa-argus");
    }

    @Test
    void searchesCaseInsensitivelyByScientificName() {
        assertThat(adapter.search(new FishSearchCriteria("CHAnNa ARGus", null, null, 0, 12)).items())
                .extracting(FishSpecies::slug)
                .containsExactly("channa-argus");
    }

    @Test
    void filtersByFamilyAndHabitat() {
        assertThat(adapter.search(new FishSearchCriteria(null, "鳜科", HabitatType.RESERVOIR, 0, 12)).items())
                .extracting(FishSpecies::slug)
                .containsExactly("siniperca-chuatsi");
    }

    @Test
    void findsDetailsBySlug() {
        assertThat(adapter.findBySlug("channa-argus")).get()
                .extracting(FishSpecies::aliases)
                .asList().contains("黑鱼");
    }

    @Test
    void findsAvailableFamiliesInNaturalOrder() {
        assertThat(adapter.findAvailableFamilies()).containsExactly("鲤科", "鳜科", "鳢科");
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

        FishPage first = adapter.search(new FishSearchCriteria(null, null, null, 0, 12));
        FishPage second = adapter.search(new FishSearchCriteria(null, null, null, 1, 12));

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
}
