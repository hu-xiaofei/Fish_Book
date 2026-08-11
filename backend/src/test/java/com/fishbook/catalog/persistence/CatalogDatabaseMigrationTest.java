package com.fishbook.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishbook.support.MySqlTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(MySqlTestConfiguration.class)
class CatalogDatabaseMigrationTest {
    @Autowired
    JdbcTemplate jdbcTemplate;

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

    @Test
    void seedsTheCuratedFishCatalogInDisplayOrderWithImageAttribution() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fish_species", Integer.class))
                .isEqualTo(12);
        assertThat(jdbcTemplate.queryForList(
                "SELECT common_name_zh FROM fish_species ORDER BY display_order", String.class))
                .containsExactly("鲫", "鲤", "草鱼", "青鱼", "鲢", "鳙", "乌鳢", "鳜", "黄颡鱼", "团头鲂", "翘嘴鲌", "泥鳅");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fish_species WHERE image_source_url = '' "
                        + "OR image_author = '' OR image_license_name = '' OR image_license_url = ''",
                Integer.class)).isZero();
    }
}
