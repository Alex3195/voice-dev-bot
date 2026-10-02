package com.alex.voicedevbot.adapter.out.stt;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TranscriptionHints;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WhisperPromptTest {

  private static final SpeechLanguage UZ = new SpeechLanguage("uz");
  private static final Map<String, String> BASE = Map.of("uz", "Bu matn lotin yozuvida.");

  @Test
  void should_use_base_prompt_of_language_when_there_is_no_vocabulary() {
    assertThat(WhisperPrompt.build(TranscriptionHints.languageOnly(UZ), BASE))
        .isEqualTo("Bu matn lotin yozuvida.");
  }

  @Test
  void should_append_vocabulary_after_base_prompt() {
    TranscriptionHints hints = new TranscriptionHints(UZ, List.of("ELT imzo", "Klaes"));

    assertThat(WhisperPrompt.build(hints, BASE))
        .isEqualTo("Bu matn lotin yozuvida. ELT imzo, Klaes.");
  }

  @Test
  void should_use_only_vocabulary_when_language_has_no_base_prompt() {
    TranscriptionHints hints = new TranscriptionHints(new SpeechLanguage("kk"), List.of("Klaes"));

    assertThat(WhisperPrompt.build(hints, BASE)).isEqualTo("Klaes.");
    assertThat(WhisperPrompt.build(TranscriptionHints.languageOnly(new SpeechLanguage("kk")), BASE))
        .isEmpty();
  }

  @Test
  void should_drop_terms_that_do_not_fit_whisper_limit() {
    List<String> manyTerms = Collections.nCopies(200, "atama");
    TranscriptionHints hints = new TranscriptionHints(UZ, manyTerms);

    String prompt = WhisperPrompt.build(hints, BASE);

    assertThat(prompt).hasSizeLessThanOrEqualTo(WhisperPrompt.MAX_LENGTH).endsWith("atama.");
  }
}
