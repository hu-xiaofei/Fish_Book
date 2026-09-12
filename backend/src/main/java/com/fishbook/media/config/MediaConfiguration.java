package com.fishbook.media.config;

import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.persistence.DisabledMediaStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MediaProperties.class)
public class MediaConfiguration {
    @Bean
    @ConditionalOnProperty(
            name = "fishbook.media.enabled",
            havingValue = "false",
            matchIfMissing = true)
    MediaStore disabledMediaStore() {
        return new DisabledMediaStore();
    }
}
