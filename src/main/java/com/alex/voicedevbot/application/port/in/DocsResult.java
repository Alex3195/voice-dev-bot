package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.Repo;
import java.util.List;
import java.util.Objects;

/** Faol project repo'sidagi hujjatlarni ko'rish natijasi. */
public sealed interface DocsResult permits DocsResult.Listed, DocsResult.Opened, RepoUnavailable {

  /**
   * @param paths {@code CLAUDE.md}, roadmap, qarorlar, spetsifikatsiyalar, keyin qolganlari
   */
  record Listed(ProjectName project, Repo repo, List<String> paths) implements DocsResult {

    public Listed {
      Objects.requireNonNull(project, "project");
      Objects.requireNonNull(repo, "repo");
      paths = List.copyOf(paths);
    }
  }

  record Opened(ProjectName project, String path, String content) implements DocsResult {

    public Opened {
      Objects.requireNonNull(project, "project");
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(content, "content");
    }
  }
}
