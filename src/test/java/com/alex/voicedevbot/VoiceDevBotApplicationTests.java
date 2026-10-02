package com.alex.voicedevbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.adapter.in.telegram.VoiceDevBot;
import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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
