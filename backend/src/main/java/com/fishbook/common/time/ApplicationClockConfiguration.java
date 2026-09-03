package com.fishbook.common.time;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ApplicationClockConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
