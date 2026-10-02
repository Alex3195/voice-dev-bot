package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.GitLabNamespace;
import com.alex.voicedevbot.domain.GitLabRepo;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.RepoLink;
import java.util.List;
import java.util.Objects;

/** Faol project ↔ GitLab repo amallari natijasi. */
public sealed interface RepoLinkResult {

  record Linked(ProjectName project, RepoLink link, ConnectionView connection)
      implements RepoLinkResult {}

  /**
   * @param connections repo tanlash uchun mavjud ulanishlar; bo'sh bo'lsa avval GitLab ulash kerak
   */
  record NotLinked(ProjectName project, List<ConnectionView> connections)
      implements RepoLinkResult {

    public NotLinked {
      connections = List.copyOf(connections);
    }
  }

  record Repos(long connectionId, String query, List<GitLabRepo> repos) implements RepoLinkResult {

    public Repos {
      Objects.requireNonNull(query, "query");
      repos = List.copyOf(repos);
    }
  }

  record Namespaces(long connectionId, List<GitLabNamespace> namespaces) implements RepoLinkResult {

    public Namespaces {
      namespaces = List.copyOf(namespaces);
    }
  }

  /** Token tugagan yoki GitLab uni rad etdi — amal o'rniga tokenni yangilash taklif qilinadi. */
  record NeedsNewToken(ConnectionView connection) implements RepoLinkResult {}

  record Failed(GitLabProblem problem) implements RepoLinkResult {}

  record NoActiveProject() implements RepoLinkResult {}

  /** Yuboruvchi whitelist'da yo'q. */
  record AccessDenied() implements RepoLinkResult {}
}
