package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.BrowseDocsUseCase;
import com.alex.voicedevbot.application.port.in.DocsResult;
import com.alex.voicedevbot.application.port.in.GitLabProblem;
import com.alex.voicedevbot.application.port.in.RepoUnavailable;
import com.alex.voicedevbot.application.port.out.GitLabApi;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public class BrowseDocsService implements BrowseDocsUseCase {

  static final String DOCS_DIRECTORY = "docs";

  /** Standart tuzilma tartibi; qolgan hujjatlar oxirida, yo'li bo'yicha. */
  private static final List<String> ORDER =
      List.of("CLAUDE.md", "README.md", "docs/roadmap.md", "docs/decisions/", "docs/specs/");

  private final ProjectRepoAccess access;
  private final GitLabApi gitLab;

  public BrowseDocsService(ProjectRepoAccess access, GitLabApi gitLab) {
    this.access = Objects.requireNonNull(access, "access");
    this.gitLab = Objects.requireNonNull(gitLab, "gitLab");
  }

  @Override
  public DocsResult list(TelegramUserId user) {
    return run(
        user,
        repo -> {
          List<String> paths =
              new ArrayList<>(gitLab.files(repo.connection(), repo.repoId(), "", false));
          paths.addAll(gitLab.files(repo.connection(), repo.repoId(), DOCS_DIRECTORY, true));
          List<String> documents =
              paths.stream()
                  .filter(BrowseDocsService::isDocument)
                  .distinct()
                  .sorted(
                      Comparator.comparingInt(BrowseDocsService::rank)
                          .thenComparing(Comparator.naturalOrder()))
                  .toList();
          return new DocsResult.Listed(repo.project(), repo.repo(), documents);
        });
  }

  @Override
  public DocsResult open(TelegramUserId user, String path) {
    Objects.requireNonNull(path, "path");
    return run(
        user,
        repo -> {
          if (!isDocument(path)) {
            return new RepoUnavailable.Failed(GitLabProblem.NOT_FOUND);
          }
          return gitLab
              .readFile(repo.connection(), repo.repoId(), path)
              .<DocsResult>map(content -> new DocsResult.Opened(repo.project(), path, content))
              .orElseGet(() -> new RepoUnavailable.Failed(GitLabProblem.NOT_FOUND));
        });
  }

  /** Ildizdagi yoki {@code docs/} ichidagi Markdown fayl. */
  static boolean isDocument(String path) {
    boolean markdown = path.toLowerCase(Locale.ROOT).endsWith(".md");
    boolean inPlace = !path.contains("/") || path.startsWith(DOCS_DIRECTORY + "/");
    return markdown && inPlace && !path.contains("..") && !path.startsWith("/");
  }

  private static int rank(String path) {
    for (int i = 0; i < ORDER.size(); i++) {
      String prefix = ORDER.get(i);
      if (prefix.endsWith("/") ? path.startsWith(prefix) : path.equals(prefix)) {
        return i;
      }
    }
    return ORDER.size();
  }

  private DocsResult run(
      TelegramUserId user, Function<ProjectRepoAccess.Ready, DocsResult> action) {
    return access.run(user, action, unavailable -> unavailable);
  }
}
