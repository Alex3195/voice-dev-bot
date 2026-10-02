package com.alex.voicedevbot.config;

import com.alex.voicedevbot.application.port.in.BrowseTranscriptsUseCase;
import com.alex.voicedevbot.application.port.in.ChangeLanguageUseCase;
import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import com.alex.voicedevbot.application.port.in.ManageGlossaryUseCase;
import com.alex.voicedevbot.application.port.in.ManageProjectsUseCase;
import com.alex.voicedevbot.application.port.out.AudioArchive;
import com.alex.voicedevbot.application.port.out.AudioSource;
import com.alex.voicedevbot.application.port.out.ProjectRepository;
import com.alex.voicedevbot.application.port.out.SpeechToText;
import com.alex.voicedevbot.application.port.out.TranscriptionLog;
import com.alex.voicedevbot.application.port.out.UserSettingsRepository;
import com.alex.voicedevbot.application.service.BrowseTranscriptsService;
import com.alex.voicedevbot.application.service.ChangeLanguageService;
import com.alex.voicedevbot.application.service.HandleVoiceMessageService;
import com.alex.voicedevbot.application.service.ManageGlossaryService;
import com.alex.voicedevbot.application.service.ManageProjectsService;
import com.alex.voicedevbot.application.service.TranscriptJournal;
import com.alex.voicedevbot.application.service.TranscriptionHintsResolver;
import com.alex.voicedevbot.application.service.UserSettingsLookup;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.time.Clock;
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
  UserSettingsLookup userSettingsLookup(
      UserSettingsRepository repository, SttProperties sttProperties) {
    return new UserSettingsLookup(repository, new SpeechLanguage(sttProperties.defaultLanguage()));
  }

  @Bean
  HandleVoiceMessageUseCase handleVoiceMessageUseCase(
      AccessPolicy accessPolicy,
      AudioSource audioSource,
      SpeechToText speechToText,
      TranscriptionHintsResolver hintsResolver,
      TranscriptJournal journal) {
    return new HandleVoiceMessageService(
        accessPolicy, audioSource, speechToText, hintsResolver, journal);
  }

  @Bean
  TranscriptionHintsResolver transcriptionHintsResolver(
      UserSettingsLookup settings, ProjectRepository projects) {
    return new TranscriptionHintsResolver(settings, projects);
  }

  @Bean
  TranscriptJournal transcriptJournal(AudioArchive archive, TranscriptionLog log, Clock clock) {
    return new TranscriptJournal(archive, log, clock);
  }

  @Bean
  BrowseTranscriptsUseCase browseTranscriptsUseCase(
      AccessPolicy accessPolicy, TranscriptionLog log) {
    return new BrowseTranscriptsService(accessPolicy, log);
  }

  @Bean
  ManageProjectsUseCase manageProjectsUseCase(
      AccessPolicy accessPolicy,
      ProjectRepository projects,
      UserSettingsLookup settings,
      UserSettingsRepository settingsRepository) {
    return new ManageProjectsService(accessPolicy, projects, settings, settingsRepository);
  }

  @Bean
  ManageGlossaryUseCase manageGlossaryUseCase(
      AccessPolicy accessPolicy, ProjectRepository projects, UserSettingsLookup settings) {
    return new ManageGlossaryService(accessPolicy, projects, settings);
  }

  @Bean
  ChangeLanguageUseCase changeLanguageUseCase(
      AccessPolicy accessPolicy,
      UserSettingsLookup settings,
      UserSettingsRepository settingsRepository) {
    return new ChangeLanguageService(accessPolicy, settings, settingsRepository);
  }
}
