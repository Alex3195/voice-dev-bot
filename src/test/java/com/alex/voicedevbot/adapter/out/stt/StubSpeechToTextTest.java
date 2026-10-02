package com.alex.voicedevbot.adapter.out.stt;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TranscriptionHints;
import org.junit.jupiter.api.Test;

class StubSpeechToTextTest {

  @Test
  void should_describe_audio_when_transcribing() {
    var transcription =
        new StubSpeechToText()
            .transcribe(
                new AudioClip(new byte[3], "audio/ogg"),
                TranscriptionHints.languageOnly(new SpeechLanguage("uz")));

    assertThat(transcription.transcript().text()).contains("3 bayt").contains("audio/ogg");
    assertThat(transcription.model()).isEqualTo("stub");
    assertThat(transcription.prompt()).isEmpty();
  }
}
