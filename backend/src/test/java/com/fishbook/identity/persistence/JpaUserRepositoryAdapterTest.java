package com.fishbook.identity.persistence;

import com.fishbook.identity.domain.User;
import com.fishbook.identity.domain.UserRole;
import com.fishbook.identity.domain.UserStatus;
import com.fishbook.support.MySqlTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({MySqlTestConfiguration.class, JpaUserRepositoryAdapter.class})
class JpaUserRepositoryAdapterTest {

    @Autowired
    private JpaUserRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndReconstructsDomainUser() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        User saved = adapter.save(User.register(
                "angler@example.com", "hashed", "Wall_E", now));
        entityManager.flush();
        entityManager.clear();

        User loaded = adapter.findByEmail("angler@example.com").orElseThrow();

        assertThat(loaded.id()).isEqualTo(saved.id());
        assertThat(loaded.email()).isEqualTo("angler@example.com");
        assertThat(loaded.passwordHash()).isEqualTo("hashed");
        assertThat(loaded.nickname()).isEqualTo("Wall_E");
        assertThat(loaded.role()).isEqualTo(UserRole.USER);
        assertThat(loaded.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(loaded.createdAt()).isEqualTo(now);
        assertThat(loaded.updatedAt()).isEqualTo(now);
    }

    @Test
    void databaseRejectsDuplicateEmail() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        adapter.save(User.register("angler@example.com", "hash-1", "One", now));

        assertThatThrownBy(() -> adapter.save(
                User.register("angler@example.com", "hash-2", "Two", now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
