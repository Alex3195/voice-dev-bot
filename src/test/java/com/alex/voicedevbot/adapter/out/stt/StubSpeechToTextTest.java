package com.alex.voicedevbot.adapter.out.stt;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.domain.AudioClip;
import org.junit.jupiter.api.Test;

class StubSpeechToTextTest {

  @Test
  void should_describe_audio_when_transcribing() {
    var transcript = new StubSpeechToText().transcribe(new AudioClip(new byte[3], "audio/ogg"));

    assertThat(transcript.text()).contains("3 bayt").contains("audio/ogg");
  }
}
