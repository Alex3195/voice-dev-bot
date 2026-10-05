package com.alex.voicedevbot.adapter.out.stt;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.domain.SpeechLanguage;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WhisperPromptTest {

  private static final Map<String, String> BASE = Map.of("uz", " Bu matn lotin yozuvida. ");

  @Test
  void should_use_base_prompt_of_language() {
    assertThat(WhisperPrompt.build(new SpeechLanguage("uz"), BASE))
        .isEqualTo("Bu matn lotin yozuvida.");
  }

  @Test
  void should_be_empty_when_language_has_no_base_prompt() {
    assertThat(WhisperPrompt.build(new SpeechLanguage("kk"), BASE)).isEmpty();
  }
}
