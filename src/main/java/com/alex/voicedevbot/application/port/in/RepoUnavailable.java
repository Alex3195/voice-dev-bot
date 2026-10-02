package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.ProjectName;
import java.util.Objects;

/** Faol projectning repo'siga murojaat qilib bo'lmadi — tasklar va hujjatlar uchun umumiy. */
public sealed interface RepoUnavailable extends TasksResult, DocsResult {

  record NoActiveProject() implements RepoUnavailable {}

  record NotLinked(ProjectName project) implements RepoUnavailable {

    public NotLinked {
      Objects.requireNonNull(project, "project");
    }
  }

  /** Token tugagan yoki GitLab uni rad etdi — amal o'rniga tokenni yangilash taklif qilinadi. */
  record NeedsNewToken(ConnectionView connection) implements RepoUnavailable {}

  record Failed(GitLabProblem problem) implements RepoUnavailable {}

  /** Yuboruvchi whitelist'da yo'q. */
  record AccessDenied() implements RepoUnavailable {}
}
