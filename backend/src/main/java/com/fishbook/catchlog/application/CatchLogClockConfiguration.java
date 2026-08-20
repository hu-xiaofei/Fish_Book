package com.fishbook.catchlog.application;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CatchLogClockConfiguration {

    @Bean
    Clock catchLogClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
