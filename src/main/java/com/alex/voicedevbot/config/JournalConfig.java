package com.alex.voicedevbot.config;

import com.alex.voicedevbot.adapter.out.archive.DiskAudioArchive;
import com.alex.voicedevbot.application.port.out.AudioArchive;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Transkripsiya jurnali uchun audio arxivi va vaqt. */
@Configuration
@EnableConfigurationProperties(JournalProperties.class)
class JournalConfig {

  @Bean
  Clock clock() {
    return Clock.systemDefaultZone();
  }

  @Bean
  AudioArchive audioArchive(JournalProperties properties, Clock clock) {
    return new DiskAudioArchive(properties.audioDir(), clock.getZone());
  }

  /** Sana papkalari va botdagi vaqtlar server mintaqasida. */
  @Bean
  ZoneId zoneId(Clock clock) {
    return clock.getZone();
  }
}
