package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.application.port.in.TaskDraftResult;
import com.alex.voicedevbot.domain.LlmUsage;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.TaskType;
import com.alex.voicedevbot.domain.TermCorrection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Claude qoralamasi haqida tasdiq ekranida ko'rsatiladigan izohlar: turi, tuzatishlar (Whisper
 * ko'proq raqam va nomlarda adashadi — tekshirish uchun), boshqa project va lug'at takliflari.
 *
 * @param correctedIn tuzatilgan matn yozilgan jurnal raqami; Claude matn qaytarmagan bo'lsa — bo'sh
 */
record ClaudeNotes(
    TaskType type,
    List<TermCorrection> corrections,
    Optional<ProjectName> otherProject,
    List<String> suggestedTerms,
    OptionalLong correctedIn,
    LlmUsage usage) {

  ClaudeNotes {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(otherProject, "otherProject");
    Objects.requireNonNull(correctedIn, "correctedIn");
    Objects.requireNonNull(usage, "usage");
    corrections = List.copyOf(corrections);
    suggestedTerms = List.copyOf(suggestedTerms);
  }

  /**
   * @param journalId qoralama tuzilgan transkript
   */
  static ClaudeNotes of(TaskDraftResult.Drafted drafted, long journalId) {
    return new ClaudeNotes(
        drafted.draft().type(),
        drafted.draft().corrections(),
        drafted.otherProject(),
        drafted.suggestedTerms(),
        drafted.correctedTranscript().isPresent()
            ? OptionalLong.of(journalId)
            : OptionalLong.empty(),
        drafted.usage());
  }

  /** Lug'atga qo'shilgan atama taklifdan olib tashlanadi. */
  ClaudeNotes without(String term) {
    return new ClaudeNotes(
        type,
        corrections,
        otherProject,
        suggestedTerms.stream().filter(suggested -> !suggested.equals(term)).toList(),
        correctedIn,
        usage);
  }
}
