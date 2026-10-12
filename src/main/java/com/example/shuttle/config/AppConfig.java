package com.example.shuttle.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableCaching      // cache settings (Caffeine, 5s TTL, max 500) live in application.yml
@EnableScheduling   // no-show reclaim + notification dispatcher
public class AppConfig {
}
