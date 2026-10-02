package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.ProjectName;
import java.util.List;

/** Lug'at buyruqlari natijasi. */
public sealed interface GlossaryCommandResult {

  /** Faol projectning (o'zgarishdan keyingi) lug'ati. */
  record Shown(ProjectName project, List<String> terms) implements GlossaryCommandResult {

    public Shown {
      terms = List.copyOf(terms);
    }
  }

  /** Lug'at faol projectga tegishli — avval project tanlanishi kerak. */
  record NoActiveProject() implements GlossaryCommandResult {}

  /** Yuboruvchi whitelist'da yo'q. */
  record AccessDenied() implements GlossaryCommandResult {}
}
