package com.alex.voicedevbot.config;

import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import com.alex.voicedevbot.application.port.out.AudioSource;
import com.alex.voicedevbot.application.port.out.SpeechToText;
import com.alex.voicedevbot.application.service.HandleVoiceMessageService;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Use-case'lar va ularning portlariga adapterlarni ulaydi. */
@Configuration
@EnableConfigurationProperties(BotProperties.class)
class ApplicationConfig {

  @Bean
  AccessPolicy accessPolicy(BotProperties properties) {
    return new AccessPolicy(
        properties.allowedUserIds().stream()
            .map(TelegramUserId::new)
            .collect(Collectors.toUnmodifiableSet()));
  }

  @Bean
  HandleVoiceMessageUseCase handleVoiceMessageUseCase(
      AccessPolicy accessPolicy, AudioSource audioSource, SpeechToText speechToText) {
    return new HandleVoiceMessageService(accessPolicy, audioSource, speechToText);
  }
}
