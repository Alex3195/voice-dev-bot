package com.alex.voicedevbot.adapter.out.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.application.port.out.AudioUnavailableException;
import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.AudioRef;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.TelegramUrl;

/** Haqiqiy OkHttp Telegram client'ini WireMock'dagi soxta Bot API serveriga qarshi tekshiradi. */
class TelegramAudioSourceIntegrationTest {

  private static final String TOKEN = "123:test";
  private static final String GET_FILE_OK =
      """
      {"ok": true, "result": {"file_id": "file-id", "file_unique_id": "u",
       "file_size": 3, "file_path": "voice/file_1.oga"}}
      """;
  private static final AudioRef REF = new AudioRef("file-id", "audio/ogg");

  @RegisterExtension
  static WireMockExtension telegramApi =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private TelegramAudioSource audioSource;

  @BeforeEach
  void setUp() {
    TelegramUrl url = new TelegramUrl("http", "localhost", telegramApi.getPort(), false);
    audioSource =
        new TelegramAudioSource(
            new OkHttpTelegramClient(TOKEN, url),
            HttpClient.newHttpClient(),
            URI.create(telegramApi.baseUrl()),
            TOKEN);
  }

  @Test
  void should_download_voice_file_when_telegram_returns_it() {
    // given
    telegramApi.stubFor(
        post(urlPathEqualTo("/bot" + TOKEN + "/getFile")).willReturn(okJson(GET_FILE_OK)));
    telegramApi.stubFor(
        get(urlPathEqualTo("/file/bot" + TOKEN + "/voice/file_1.oga"))
            .willReturn(aResponse().withBody(new byte[] {1, 2, 3})));

    // when
    AudioClip clip = audioSource.fetch(REF);

    // then
    assertThat(clip).isEqualTo(new AudioClip(new byte[] {1, 2, 3}, "audio/ogg"));
  }

  @Test
  void should_throw_audio_unavailable_when_telegram_rejects_request() {
    telegramApi.stubFor(
        post(urlPathEqualTo("/bot" + TOKEN + "/getFile"))
            .willReturn(
                okJson(
                    """
                    {"ok": false, "error_code": 400, "description": "Bad Request: invalid file_id"}
                    """)));

    assertThatThrownBy(() -> audioSource.fetch(REF))
        .isInstanceOf(AudioUnavailableException.class)
        .hasMessageContaining("file-id");
  }

  @Test
  void should_throw_audio_unavailable_without_leaking_token_when_file_download_fails() {
    telegramApi.stubFor(
        post(urlPathEqualTo("/bot" + TOKEN + "/getFile")).willReturn(okJson(GET_FILE_OK)));
    telegramApi.stubFor(
        get(urlPathEqualTo("/file/bot" + TOKEN + "/voice/file_1.oga"))
            .willReturn(aResponse().withStatus(404)));

    assertThatThrownBy(() -> audioSource.fetch(REF))
        .isInstanceOf(AudioUnavailableException.class)
        .hasRootCauseMessage("Unexpected HTTP 404 for Telegram file file-id")
        .satisfies(e -> assertThat(e.getMessage()).doesNotContain(TOKEN));
  }
}
