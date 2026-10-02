package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.application.port.in.TaskDraftResult;
import com.alex.voicedevbot.domain.LlmUsage;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.TaskType;
import com.alex.voicedevbot.domain.TermCorrection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Claude qoralamasi haqida tasdiq ekranida ko'rsatiladigan izohlar: turi, tuzatishlar (Whisper
 * ko'proq raqam va nomlarda adashadi — tekshirish uchun), boshqa project va lug'at takliflari.
 */
record ClaudeNotes(
    TaskType type,
    List<TermCorrection> corrections,
    Optional<ProjectName> otherProject,
    List<String> suggestedTerms,
    LlmUsage usage) {

  ClaudeNotes {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(otherProject, "otherProject");
    Objects.requireNonNull(usage, "usage");
    corrections = List.copyOf(corrections);
    suggestedTerms = List.copyOf(suggestedTerms);
  }

  static ClaudeNotes of(TaskDraftResult.Drafted drafted) {
    return new ClaudeNotes(
        drafted.draft().type(),
        drafted.draft().corrections(),
        drafted.otherProject(),
        drafted.suggestedTerms(),
        drafted.usage());
  }

  /** Lug'atga qo'shilgan atama taklifdan olib tashlanadi. */
  ClaudeNotes without(String term) {
    return new ClaudeNotes(
        type,
        corrections,
        otherProject,
        suggestedTerms.stream().filter(suggested -> !suggested.equals(term)).toList(),
        usage);
  }
}
