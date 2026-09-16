package com.fishbook.media.config;

import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.persistence.FilesystemMediaStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "fishbook.media.enabled", havingValue = "true")
@ConditionalOnProperty(name = "fishbook.media.provider", havingValue = "filesystem")
@EnableConfigurationProperties(FilesystemProperties.class)
public class FilesystemConfiguration {
    @Bean
    MediaStore filesystemMediaStore(FilesystemProperties properties) {
        return new FilesystemMediaStore(properties.root());
    }
}
