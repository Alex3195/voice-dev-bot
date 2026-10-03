package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.LlmUsage;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.TaskDraft;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Transkriptdan qoralama natijasi. */
public sealed interface TaskDraftResult {

  /**
   * @param otherProject Claude task boshqa (mavjud) projectga tegishli deb topdi — faol project
   *     emas
   * @param suggestedTerms faol project lug'atiga qo'shish taklifi (Whisper adashgan, lug'atda yo'q
   *     atamalar)
   * @param correctedTranscript Claude tuzatgan transkript (jurnalga ham yozilgan)
   */
  record Drafted(
      TaskDraft draft,
      Optional<ProjectName> otherProject,
      List<String> suggestedTerms,
      Optional<String> correctedTranscript,
      LlmUsage usage)
      implements TaskDraftResult {

    public Drafted {
      Objects.requireNonNull(draft, "draft");
      Objects.requireNonNull(otherProject, "otherProject");
      Objects.requireNonNull(correctedTranscript, "correctedTranscript");
      Objects.requireNonNull(usage, "usage");
      suggestedTerms = List.copyOf(suggestedTerms);
    }
  }

  record Failed(LanguageModelProblem problem) implements TaskDraftResult {}

  record NotFound() implements TaskDraftResult {}

  /** Yuboruvchi whitelist'da yo'q. */
  record AccessDenied() implements TaskDraftResult {}
}
