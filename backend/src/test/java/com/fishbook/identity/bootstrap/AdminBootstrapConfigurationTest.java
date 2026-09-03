package com.fishbook.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishbook.identity.domain.PasswordHasher;
import com.fishbook.identity.domain.User;
import com.fishbook.identity.domain.UserRepository;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AdminBootstrapConfigurationTest {

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
    void enabledBootstrapAcceptsCompleteConfiguration() {
        runner.withPropertyValues(validProperties())
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void enabledBootstrapRejectsBlankConfiguration() {
        runner.withPropertyValues(validProperties())
                .withPropertyValues("fishbook.admin.bootstrap.email=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledBootstrapRejectsMissingEmail() {
        assertMissingEnabledPropertyFails("fishbook.admin.bootstrap.email");
    }

    @Test
    void enabledBootstrapRejectsMissingPassword() {
        assertMissingEnabledPropertyFails("fishbook.admin.bootstrap.password");
    }

    @Test
    void enabledBootstrapRejectsMissingNickname() {
        assertMissingEnabledPropertyFails("fishbook.admin.bootstrap.nickname");
    }

    private void assertMissingEnabledPropertyFails(String missingProperty) {
        runner.withPropertyValues(propertiesWithout(missingProperty))
                .run(context -> assertThat(context).hasFailed());
    }

    private static String[] validProperties() {
        return new String[]{
                "fishbook.admin.bootstrap.enabled=true",
                "fishbook.admin.bootstrap.email=admin@example.com",
                "fishbook.admin.bootstrap.password=strong-admin-pass",
                "fishbook.admin.bootstrap.nickname=管理员"
        };
    }

    private static String[] propertiesWithout(String missingProperty) {
        return java.util.Arrays.stream(validProperties())
                .filter(property -> !property.startsWith(missingProperty + "="))
                .toArray(String[]::new);
    }

    private static final class FakeUserRepository implements UserRepository {

        @Override
        public boolean existsByEmail(String normalizedEmail) {
            return false;
        }

        @Override
        public Optional<User> findByEmail(String normalizedEmail) {
            return Optional.empty();
        }

        @Override
        public User save(User user) {
            return user;
        }
    }

    private static final class FakePasswordHasher implements PasswordHasher {

        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return false;
        }
    }
}
