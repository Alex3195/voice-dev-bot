package com.alex.voicedevbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.adapter.in.telegram.VoiceDevBot;
import com.alex.voicedevbot.adapter.out.stt.StubSpeechToText;
import com.alex.voicedevbot.adapter.out.stt.WhisperCppSpeechToText;
import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import com.alex.voicedevbot.application.port.out.SpeechToText;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

/** Smoke: Spring konteksti to'g'ri yig'iladi, Telegram'ga ulanmasdan. */
@SpringBootTest(
    properties = {"bot.token=123:test", "bot.allowed-user-ids=1,2", "bot.polling-enabled=false"})
class VoiceDevBotApplicationTests {

  @Autowired ApplicationContext context;

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
            "--bot.token=123:test",
            "--bot.allowed-user-ids=1",
            "--bot.polling-enabled=false",
            "--stt.engine=stub",
            "--spring.config.import=")) {
      assertThat(stubContext.getBean(SpeechToText.class)).isInstanceOf(StubSpeechToText.class);
    }
  }

  @Test
  void should_fail_to_start_when_token_is_missing() {
    SpringApplication app = new SpringApplication(VoiceDevBotApplication.class);

    assertThatThrownBy(
            () ->
                app.run(
                    "--bot.token=",
                    "--bot.allowed-user-ids=1",
                    "--bot.polling-enabled=false",
                    "--spring.config.import="))
        .hasStackTraceContaining("bot.token");
  }
}
