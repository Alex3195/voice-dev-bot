package com.alex.voicedevbot.config;

import com.alex.voicedevbot.adapter.in.telegram.TelegramCommands;
import com.alex.voicedevbot.adapter.in.telegram.VoiceDevBot;
import com.alex.voicedevbot.adapter.out.telegram.TelegramAudioSource;
import com.alex.voicedevbot.application.port.in.ChangeLanguageUseCase;
import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import com.alex.voicedevbot.application.port.in.ManageGlossaryUseCase;
import com.alex.voicedevbot.application.port.in.ManageProjectsUseCase;
import com.alex.voicedevbot.application.port.out.AudioSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.TelegramUrl;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** Telegram client, bot va long polling sessiyasi. */
@Configuration
class TelegramConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  @Bean
  TelegramClient telegramClient(BotProperties properties) {
    URI api = properties.apiUrl();
    TelegramUrl telegramUrl = new TelegramUrl(api.getScheme(), api.getHost(), portOf(api), false);
    return new OkHttpTelegramClient(properties.token(), telegramUrl);
  }

  @Bean
  AudioSource audioSource(TelegramClient telegramClient, BotProperties properties) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    return new TelegramAudioSource(
        telegramClient, httpClient, properties.apiUrl(), properties.token());
  }

  @Bean
  TelegramCommands telegramCommands(
      ManageProjectsUseCase projects,
      ManageGlossaryUseCase glossary,
      ChangeLanguageUseCase language) {
    return new TelegramCommands(projects, glossary, language);
  }

  @Bean
  VoiceDevBot voiceDevBot(
      HandleVoiceMessageUseCase handleVoiceMessageUseCase,
      TelegramCommands telegramCommands,
      TelegramClient telegramClient) {
    return new VoiceDevBot(handleVoiceMessageUseCase, telegramCommands, telegramClient);
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "bot.polling-enabled", havingValue = "true", matchIfMissing = true)
  TelegramBotsLongPollingApplication telegramBotsApplication() {
    return new TelegramBotsLongPollingApplication();
  }

  @Bean
  @ConditionalOnProperty(name = "bot.polling-enabled", havingValue = "true", matchIfMissing = true)
  ApplicationRunner registerBot(
      TelegramBotsLongPollingApplication application,
      BotProperties properties,
      VoiceDevBot voiceDevBot) {
    return args -> {
      application.registerBot(properties.token(), voiceDevBot);
      voiceDevBot.publishCommandMenu();
    };
  }

  private static int portOf(URI uri) {
    if (uri.getPort() != -1) {
      return uri.getPort();
    }
    return "http".equals(uri.getScheme()) ? 80 : 443;
  }
}
