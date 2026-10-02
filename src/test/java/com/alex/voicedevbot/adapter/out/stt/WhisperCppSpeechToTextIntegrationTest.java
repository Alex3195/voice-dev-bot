package com.alex.voicedevbot.adapter.out.stt;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.application.port.out.TranscriptionException;
import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.Transcript;
import com.alex.voicedevbot.domain.Transcription;
import com.alex.voicedevbot.domain.TranscriptionHints;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** {@link WhisperCppSpeechToText}ni WireMock'dagi soxta whisper-server'ga qarshi tekshiradi. */
class WhisperCppSpeechToTextIntegrationTest {

  private static final AudioClip AUDIO = new AudioClip(new byte[] {1, 2, 3}, "audio/ogg");
  private static final Duration TIMEOUT = Duration.ofSeconds(2);
  private static final Map<String, String> BASE_PROMPTS = Map.of("uz", "Lotin yozuvida.");
  private static final TranscriptionHints HINTS =
      new TranscriptionHints(new SpeechLanguage("uz"), List.of("ELT imzo", "kassa bo'limi"));

  private static final String MODEL = "ggml-large-v3-q5_0";

  @RegisterExtension
  static WireMockExtension whisper =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private WhisperCppSpeechToText speechToText;

  @BeforeEach
  void setUp() {
    speechToText = whisperAt(whisper.baseUrl(), BASE_PROMPTS);
  }

  private static WhisperCppSpeechToText whisperAt(String url, Map<String, String> basePrompts) {
    return new WhisperCppSpeechToText(
        HttpClient.newHttpClient(),
        new WhisperCppSettings(URI.create(url), MODEL, basePrompts, TIMEOUT));
  }

  @Test
  void
      should_return_stripped_transcript_with_sent_prompt_and_model_when_server_recognizes_speech() {
    whisper.stubFor(post(urlPathEqualTo("/inference")).willReturn(ok(" Yangi task yarating.\n")));

    Transcription transcription = speechToText.transcribe(AUDIO, HINTS);

    assertThat(transcription)
        .isEqualTo(
            new Transcription(
                new Transcript("Yangi task yarating."),
                "Lotin yozuvida. ELT imzo, kassa bo'limi.",
                "whisper.cpp " + MODEL));
  }

  @Test
  void should_send_audio_language_and_text_format_when_transcribing() {
    whisper.stubFor(post(urlPathEqualTo("/inference")).willReturn(ok("salom")));

    speechToText.transcribe(AUDIO, HINTS);

    whisper.verify(
        postRequestedFor(urlPathEqualTo("/inference"))
            .withAnyRequestBodyPart(
                aMultipart("file")
                    .withHeader("Content-Type", equalTo("audio/ogg"))
                    .withBody(binaryEqualTo(new byte[] {1, 2, 3})))
            .withAnyRequestBodyPart(aMultipart("language").withBody(equalTo("uz")))
            .withAnyRequestBodyPart(aMultipart("response_format").withBody(equalTo("text")))
            .withAnyRequestBodyPart(
                aMultipart("prompt")
                    .withBody(equalTo("Lotin yozuvida. ELT imzo, kassa bo'limi."))));
  }

  @Test
  void should_not_send_prompt_when_it_is_blank() {
    whisper.stubFor(post(urlPathEqualTo("/inference")).willReturn(ok("salom")));

    whisperAt(whisper.baseUrl(), Map.of())
        .transcribe(AUDIO, TranscriptionHints.languageOnly(new SpeechLanguage("uz")));

    assertThat(whisper.getAllServeEvents().getFirst().getRequest().getBodyAsString())
        .contains("name=\"language\"")
        .doesNotContain("name=\"prompt\"");
  }

  @Test
  void should_throw_transcription_exception_when_server_fails() {
    whisper.stubFor(
        post(urlPathEqualTo("/inference"))
            .willReturn(serverError().withBody("{\"error\":\"FFmpeg conversion failed.\"}")));

    assertThatThrownBy(() -> speechToText.transcribe(AUDIO, HINTS))
        .isInstanceOf(TranscriptionException.class)
        .hasMessageContaining("whisper-server")
        .hasRootCauseMessage("Unexpected HTTP 500 from whisper-server");
  }

  @Test
  void should_throw_transcription_exception_when_transcript_is_blank() {
    whisper.stubFor(post(urlPathEqualTo("/inference")).willReturn(ok("  \n")));

    assertThatThrownBy(() -> speechToText.transcribe(AUDIO, HINTS))
        .isInstanceOf(TranscriptionException.class)
        .hasRootCauseMessage("whisper-server returned an empty transcript");
  }

  @Test
  void should_throw_transcription_exception_when_server_is_too_slow() {
    whisper.stubFor(
        post(urlPathEqualTo("/inference"))
            .willReturn(aResponse().withBody("kech").withFixedDelay(3_000)));

    assertThatThrownBy(() -> speechToText.transcribe(AUDIO, HINTS))
        .isInstanceOf(TranscriptionException.class)
        .hasCauseInstanceOf(HttpTimeoutException.class);
  }

  @Test
  void should_throw_transcription_exception_when_server_is_unreachable() {
    WhisperCppSpeechToText unreachable = whisperAt("http://localhost:1", BASE_PROMPTS);

    assertThatThrownBy(() -> unreachable.transcribe(AUDIO, HINTS))
        .isInstanceOf(TranscriptionException.class);
  }
}
