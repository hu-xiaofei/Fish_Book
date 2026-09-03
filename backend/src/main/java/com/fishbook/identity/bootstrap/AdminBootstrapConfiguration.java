package com.fishbook.identity.bootstrap;

import com.fishbook.identity.domain.PasswordHasher;
import com.fishbook.identity.domain.UserRepository;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AdminBootstrapConfiguration {

    @Bean
    AdminBootstrapService adminBootstrapService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            Clock clock) {
        return new AdminBootstrapService(userRepository, passwordHasher, clock);
    }

    @Bean
    ApplicationRunner adminBootstrapRunner(
            AdminBootstrapService adminBootstrapService,
            AdminBootstrapProperties properties) {
        return arguments -> adminBootstrapService.bootstrap(properties);
    }
}
