package com.alex.voicedevbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.adapter.in.telegram.VoiceDevBot;
import com.alex.voicedevbot.adapter.out.stt.StubSpeechToText;
import com.alex.voicedevbot.adapter.out.stt.WhisperCppSpeechToText;
import com.alex.voicedevbot.application.port.in.ChangeLanguageUseCase;
import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import com.alex.voicedevbot.application.port.in.LinkRepoUseCase;
import com.alex.voicedevbot.application.port.in.ManageGitLabUseCase;
import com.alex.voicedevbot.application.port.in.ManageGlossaryUseCase;
import com.alex.voicedevbot.application.port.in.ManageProjectsUseCase;
import com.alex.voicedevbot.application.port.out.ProjectRepository;
import com.alex.voicedevbot.application.port.out.SpeechToText;
import com.alex.voicedevbot.support.PostgresContainer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

/** Smoke: Spring konteksti to'g'ri yig'iladi, Telegram'ga ulanmasdan. */
@SpringBootTest(
    properties = {
      "bot.token=123:test",
      "bot.allowed-user-ids=1,2",
      "bot.polling-enabled=false",
      "secrets.key=" + VoiceDevBotApplicationTests.SECRETS_KEY
    })
class VoiceDevBotApplicationTests {

  /** 32 bayt Base64 — faqat testlar uchun. */
  static final String SECRETS_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

  @Autowired ApplicationContext context;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    var postgres = PostgresContainer.instance();
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Test
  void should_wire_command_use_cases_and_migrate_database() {
    assertThat(context.getBean(ManageProjectsUseCase.class)).isNotNull();
    assertThat(context.getBean(ManageGlossaryUseCase.class)).isNotNull();
    assertThat(context.getBean(ChangeLanguageUseCase.class)).isNotNull();
    assertThat(context.getBean(ProjectRepository.class).findAll()).isNotNull();
  }

  @Test
  void should_wire_bot_and_use_case_when_context_starts() {
    assertThat(context.getBean(VoiceDevBot.class)).isNotNull();
    assertThat(context.getBean(HandleVoiceMessageUseCase.class)).isNotNull();
    assertThat(context.getBeansOfType(TelegramBotsLongPollingApplication.class)).isEmpty();
  }

  @Test
  void should_use_whisper_cpp_stt_when_engine_is_not_configured() {
    assertThat(context.getBean(SpeechToText.class)).isInstanceOf(WhisperCppSpeechToText.class);
  }

  @Test
  void should_use_stub_stt_when_engine_is_stub() {
    SpringApplication app = new SpringApplication(VoiceDevBotApplication.class);

    try (ConfigurableApplicationContext stubContext =
        app.run(
            withDatasource(
                "--bot.token=123:test",
                "--bot.allowed-user-ids=1",
                "--bot.polling-enabled=false",
                "--stt.engine=stub",
                "--secrets.key=" + SECRETS_KEY,
                "--spring.config.import="))) {
      assertThat(stubContext.getBean(SpeechToText.class)).isInstanceOf(StubSpeechToText.class);
    }
  }

  @Test
  void should_fail_to_start_when_token_is_missing() {
    SpringApplication app = new SpringApplication(VoiceDevBotApplication.class);

    assertThatThrownBy(
            () ->
                app.run(
                    withDatasource(
                        "--bot.token=",
                        "--bot.allowed-user-ids=1",
                        "--bot.polling-enabled=false",
                        "--secrets.key=" + SECRETS_KEY,
                        "--spring.config.import=")))
        .hasStackTraceContaining("bot.token");
  }

  @Test
  void should_fail_to_start_when_secrets_key_is_missing_or_not_32_bytes() {
    for (String key : new String[] {"", "c2hvcnQ="}) {
      SpringApplication app = new SpringApplication(VoiceDevBotApplication.class);

      assertThatThrownBy(
              () ->
                  app.run(
                      withDatasource(
                          "--bot.token=123:test",
                          "--bot.allowed-user-ids=1",
                          "--bot.polling-enabled=false",
                          "--secrets.key=" + key,
                          "--spring.config.import=")))
          .hasStackTraceContaining(key.isEmpty() ? "secrets.key" : "SECRETS_KEY must be 32 bytes");
    }
  }

  @Test
  void should_wire_gitlab_use_cases() {
    assertThat(context.getBean(ManageGitLabUseCase.class)).isNotNull();
    assertThat(context.getBean(LinkRepoUseCase.class)).isNotNull();
  }

  private static String[] withDatasource(String... args) {
    return Stream.concat(Stream.of(args), Stream.of(PostgresContainer.datasourceArgs()))
        .toArray(String[]::new);
  }
}
