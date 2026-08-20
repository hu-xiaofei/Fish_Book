package com.fishbook.catchlog.persistence;

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
class CatchRecordDatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsCatchRecordsWithOwnershipForeignKeysAndStableSortIndex() {
        // Bug caught: an absent or underspecified table could bypass owner scoping or stable paging.
        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class)).contains("catch_records");
        assertThat(jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name = 'catch_records'",
                String.class)).contains("PRIMARY", "fk_catch_records_user", "fk_catch_records_fish");
        assertThat(jdbcTemplate.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'catch_records'",
                String.class)).contains("ix_catch_records_user_caught_created_id");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'catch_records' "
                        + "AND column_name = 'photo_object_key'",
                String.class)).isEqualTo("YES");

        List<String> deleteRules = jdbcTemplate.queryForList(
                "SELECT delete_rule FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE() "
                        + "AND constraint_name IN ('fk_catch_records_user', 'fk_catch_records_fish')",
                String.class);
        assertThat(deleteRules).containsExactlyInAnyOrder("RESTRICT", "RESTRICT");
    }
}
