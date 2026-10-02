package com.alex.voicedevbot.domain;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Claude transkriptdan tuzgan task qoralamasi. Foydalanuvchi tasdiqlamaguncha hech narsa
 * yaratilmaydi.
 *
 * @param summary bir gapli xulosa — project tarixi uchun
 */
public record TaskDraft(
    String title,
    String description,
    List<String> acceptanceCriteria,
    TaskType type,
    List<TermCorrection> corrections,
    String summary) {

  public TaskDraft {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(summary, "summary");
    title = title.strip();
    description = description.strip();
    summary = summary.strip();
    if (title.isEmpty() || title.length() > NewTask.MAX_TITLE_LENGTH) {
      throw new IllegalArgumentException(
          "Task title must be 1-" + NewTask.MAX_TITLE_LENGTH + " characters");
    }
    acceptanceCriteria =
        acceptanceCriteria.stream().map(String::strip).filter(item -> !item.isEmpty()).toList();
    corrections = List.copyOf(corrections);
  }

  /** Issue: tavsif va acceptance criteria — {@code .ai/task-template.md} bo'limlari bilan. */
  public NewTask toNewTask() {
    StringBuilder body = new StringBuilder("## Talab\n").append(description);
    if (!acceptanceCriteria.isEmpty()) {
      body.append("\n\n## Acceptance criteria\n")
          .append(
              acceptanceCriteria.stream()
                  .map(item -> "- [ ] " + item)
                  .collect(Collectors.joining("\n")));
    }
    return new NewTask(title, body.toString());
  }
}
