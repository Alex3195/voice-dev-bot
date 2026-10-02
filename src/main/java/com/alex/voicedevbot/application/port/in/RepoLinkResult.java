package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.Namespace;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.Repo;
import com.alex.voicedevbot.domain.RepoLink;
import java.util.List;
import java.util.Objects;

/** Faol project ↔ repo amallari natijasi. */
public sealed interface RepoLinkResult {

  record Linked(ProjectName project, RepoLink link, ConnectionView connection)
      implements RepoLinkResult {}

  /**
   * @param connections repo tanlash uchun mavjud ulanishlar; bo'sh bo'lsa avval ulanish qo'shish
   *     kerak
   */
  record NotLinked(ProjectName project, List<ConnectionView> connections)
      implements RepoLinkResult {

    public NotLinked {
      connections = List.copyOf(connections);
    }
  }

  record Repos(long connectionId, String query, List<Repo> repos) implements RepoLinkResult {

    public Repos {
      Objects.requireNonNull(query, "query");
      repos = List.copyOf(repos);
    }
  }

  record Namespaces(long connectionId, List<Namespace> namespaces) implements RepoLinkResult {

    public Namespaces {
      namespaces = List.copyOf(namespaces);
    }
  }

  /** Token tugagan yoki xizmat uni rad etdi — amal o'rniga tokenni yangilash taklif qilinadi. */
  record NeedsNewToken(ConnectionView connection) implements RepoLinkResult {}

  record Failed(ConnectionProblem problem) implements RepoLinkResult {}

  record NoActiveProject() implements RepoLinkResult {}

  /** Yuboruvchi whitelist'da yo'q. */
  record AccessDenied() implements RepoLinkResult {}
}
