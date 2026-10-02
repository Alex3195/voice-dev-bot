package com.alex.voicedevbot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AudioClipTest {

  @Test
  void should_reject_clip_when_audio_is_empty() {
    assertThatThrownBy(() -> new AudioClip(new byte[0], "audio/ogg"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AudioClip(null, "audio/ogg"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_stay_immutable_when_source_or_returned_bytes_are_modified() {
    byte[] source = {1, 2, 3};
    AudioClip clip = new AudioClip(source, "audio/ogg");

    source[0] = 9;
    clip.bytes()[1] = 9;

    assertThat(clip.bytes()).containsExactly(1, 2, 3);
    assertThat(clip.size()).isEqualTo(3);
  }

  @Test
  void should_be_equal_when_bytes_and_mime_type_match() {
    AudioClip clip = new AudioClip(new byte[] {1, 2}, "audio/ogg");

    assertThat(clip)
        .isEqualTo(new AudioClip(new byte[] {1, 2}, "audio/ogg"))
        .hasSameHashCodeAs(new AudioClip(new byte[] {1, 2}, "audio/ogg"))
        .isNotEqualTo(new AudioClip(new byte[] {1, 2}, "audio/mpeg"))
        .isNotEqualTo(new AudioClip(new byte[] {1, 3}, "audio/ogg"));
  }

  @Test
  void should_not_print_bytes_in_to_string() {
    assertThat(new AudioClip(new byte[] {1, 2}, "audio/ogg"))
        .hasToString("AudioClip[size=2, mimeType=audio/ogg]");
  }
}
