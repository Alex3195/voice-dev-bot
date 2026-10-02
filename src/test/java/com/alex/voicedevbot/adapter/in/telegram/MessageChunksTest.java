package com.alex.voicedevbot.adapter.in.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MessageChunksTest {

  @Test
  void should_keep_text_in_one_chunk_when_it_fits() {
    assertThat(MessageChunks.split("yangi task", 20)).containsExactly("yangi task");
  }

  @Test
  void should_split_on_word_boundary_when_text_is_too_long() {
    assertThat(MessageChunks.split("bir ikki uch tort", 9)).containsExactly("bir ikki", "uch tort");
  }

  @Test
  void should_cut_at_limit_when_word_is_longer_than_limit() {
    assertThat(MessageChunks.split("abcdefghij", 4)).containsExactly("abcd", "efgh", "ij");
  }

  @Test
  void should_never_exceed_telegram_limit_and_keep_all_words() {
    String text = "so'z ".repeat(3_000).strip();

    List<String> chunks = MessageChunks.split(text, MessageChunks.MAX_MESSAGE_LENGTH);

    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(4096));
    assertThat(String.join(" ", chunks)).isEqualTo(text);
  }
}
