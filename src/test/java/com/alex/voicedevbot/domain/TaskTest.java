package com.alex.voicedevbot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TaskTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

  private static Task task(boolean open, String dueDate, int mergeRequests) {
    return new Task(
        12,
        "Login",
        "",
        open,
        dueDate.isEmpty() ? Optional.empty() : Optional.of(LocalDate.parse(dueDate)),
        mergeRequests,
        URI.create("https://gitlab.com/alex/elt-imzo/-/issues/12"));
  }

  @ParameterizedTest(name = "open={0}, due={1}, MR={2} → {3}")
  @CsvSource({
    "true, '', 0, OPEN",
    "true, 2026-10-02, 0, OPEN",
    "true, 2026-10-01, 0, OVERDUE",
    "true, 2026-10-01, 1, OVERDUE",
    "true, '', 2, IN_REVIEW",
    "false, '', 1, DONE",
    "false, 2026-09-01, 0, CLOSED"
  })
  void should_derive_status_from_state_due_date_and_merge_requests(
      boolean open, String dueDate, int mergeRequests, TaskStatus expected) {
    assertThat(task(open, dueDate, mergeRequests).status(TODAY)).isEqualTo(expected);
  }

  @Test
  void should_reject_negative_merge_request_count() {
    assertThatThrownBy(() -> task(true, "", -1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_strip_new_task_fields() {
    assertThat(new NewTask("  Login  ", "\n tavsif \n")).isEqualTo(new NewTask("Login", "tavsif"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, NewTask.MAX_TITLE_LENGTH + 1})
  void should_reject_empty_or_too_long_title(int length) {
    assertThatThrownBy(() -> new NewTask("x".repeat(length), ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_take_first_sentence_as_title_and_keep_full_text_as_description() {
    String text = "Login sahifasini tuzatish kerak. Parol esdan chiqsa, SMS yuborilsin!";

    NewTask task = NewTask.fromText(text, 80);

    assertThat(task.title()).isEqualTo("Login sahifasini tuzatish kerak");
    assertThat(task.description()).isEqualTo(text);
  }

  @Test
  void should_shorten_long_first_sentence_with_ellipsis() {
    NewTask task = NewTask.fromText("kassa   bo'limida hisobot chiqmayapti", 12);

    assertThat(task.title()).isEqualTo("kassa bo'li…").hasSize(12);
  }

  @Test
  void should_not_split_sentence_inside_version_number() {
    assertThat(NewTask.fromText("v1.2 ga yangilash. Keyin test", 80).title())
        .isEqualTo("v1.2 ga yangilash");
  }

  @Test
  void should_use_whole_text_when_there_is_no_sentence_end() {
    assertThat(NewTask.fromText("hisobot qo'shish", 80).title()).isEqualTo("hisobot qo'shish");
  }
}
