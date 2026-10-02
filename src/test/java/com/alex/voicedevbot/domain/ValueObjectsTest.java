package com.alex.voicedevbot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ValueObjectsTest {

  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void should_reject_user_id_when_not_positive(long value) {
    assertThatThrownBy(() -> new TelegramUserId(value))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "   ")
  void should_reject_transcript_when_text_is_blank(String text) {
    assertThatThrownBy(() -> new Transcript(text)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_strip_transcript_text() {
    assertThat(new Transcript("  salom  ").text()).isEqualTo("salom");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void should_reject_audio_ref_when_id_or_mime_type_is_blank(String value) {
    assertThatThrownBy(() -> new AudioRef(value, "audio/ogg"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AudioRef("file-id", value))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
