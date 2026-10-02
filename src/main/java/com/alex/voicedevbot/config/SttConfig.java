package com.alex.voicedevbot.config;

import com.alex.voicedevbot.adapter.out.stt.StubSpeechToText;
import com.alex.voicedevbot.adapter.out.stt.WhisperCppSpeechToText;
import com.alex.voicedevbot.application.port.out.SpeechToText;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** {@code stt.engine} bo'yicha {@link SpeechToText} adapterini tanlaydi. */
@Configuration
@EnableConfigurationProperties(SttProperties.class)
class SttConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

  @Bean
  @ConditionalOnProperty(name = "stt.engine", havingValue = "whisper-cpp", matchIfMissing = true)
  SpeechToText whisperCppSpeechToText(SttProperties properties) {
    SttProperties.Whisper whisper = properties.whisper();
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    return new WhisperCppSpeechToText(
        httpClient, whisper.url(), whisper.language(), whisper.timeout());
  }

  @Bean
  @ConditionalOnProperty(name = "stt.engine", havingValue = "stub")
  SpeechToText stubSpeechToText() {
    return new StubSpeechToText();
  }
}
