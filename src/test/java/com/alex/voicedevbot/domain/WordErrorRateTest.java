package com.alex.voicedevbot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.alex.voicedevbot.domain.WordErrorRate.WordError;
import com.alex.voicedevbot.domain.WordErrorRate.WordError.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

class WordErrorRateTest {

  @Test
  void should_be_zero_when_only_case_punctuation_and_apostrophe_shape_differ() {
    WordErrorRate wer =
        WordErrorRate.of("O'zbekiston, Toshkent shahri.", "oʻzbekiston toshkent  SHAHRI");

    assertThat(wer.rate()).isZero();
    assertThat(wer.referenceWords()).isEqualTo(3);
  }

  @Test
  void should_count_substitution_deletion_and_insertion() {
    WordErrorRate wer =
        WordErrorRate.of("elt imzo sahifasida muddat chiqsin", "elt imza sahifasida chiqsin endi");

    assertThat(wer.errors())
        .containsExactly(
            new WordError("imzo", "imza"), new WordError("muddat", ""), new WordError("", "endi"));
    assertThat(wer.count(Kind.SUBSTITUTION)).isEqualTo(1);
    assertThat(wer.count(Kind.DELETION)).isEqualTo(1);
    assertThat(wer.count(Kind.INSERTION)).isEqualTo(1);
    assertThat(wer.rate()).isCloseTo(3 / 5.0, within(1e-9));
  }

  @Test
  void should_count_other_script_as_errors() {
    assertThat(WordErrorRate.of("salom dunyo", "салом dunyo").errors())
        .containsExactly(new WordError("salom", "салом"));
  }

  @Test
  void should_weigh_combined_rate_by_reference_words() {
    WordErrorRate shortOne = WordErrorRate.of("bir ikki", "bir uch");
    WordErrorRate longOne = WordErrorRate.of("a b c d e f g h", "a b c d e f g h");

    WordErrorRate total = WordErrorRate.none().plus(shortOne).plus(longOne);

    assertThat(total.referenceWords()).isEqualTo(10);
    assertThat(total.rate()).isCloseTo(0.1, within(1e-9));
  }

  @Test
  void should_handle_empty_texts() {
    assertThat(WordErrorRate.of("", "").rate()).isZero();
    assertThat(WordErrorRate.of("...", "nimadir").rate()).isEqualTo(1);
    assertThat(WordErrorRate.of("bir ikki", "").errors())
        .containsExactly(new WordError("bir", ""), new WordError("ikki", ""));
  }

  @Test
  void should_strip_quote_apostrophes_but_keep_inner_ones() {
    assertThat(WordErrorRate.words("'Salom' — dedi, g'alaba 2026-yil"))
        .containsExactly("salom", "dedi", "g'alaba", "2026", "yil");
  }

  @Test
  void should_reject_invalid_values() {
    assertThatThrownBy(() -> new WordErrorRate(-1, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new WordError("", "")).isInstanceOf(IllegalArgumentException.class);
  }
}
