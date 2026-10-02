package com.alex.voicedevbot.support;

import com.alex.voicedevbot.domain.GitLabNamespace;
import com.alex.voicedevbot.domain.GitLabRepo;
import com.alex.voicedevbot.domain.GitLabToken;
import com.alex.voicedevbot.domain.TokenInfo;
import java.net.URI;
import java.time.LocalDate;
import java.util.Set;

/** GitLab testlari uchun umumiy qiymatlar. */
public final class GitLabFixtures {

  public static final GitLabToken TOKEN = new GitLabToken("glpat-secretToken1234");
  public static final GitLabToken NEW_TOKEN = new GitLabToken("glpat-newSecretToken99");
  public static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
  public static final TokenInfo VALID =
      TokenInfo.expiring("alex", Set.of("api"), LocalDate.of(2027, 1, 1));
  public static final GitLabRepo REPO =
      new GitLabRepo(42, "alex/elt-imzo", URI.create("https://gitlab.com/alex/elt-imzo"));
  public static final GitLabNamespace PERSONAL = new GitLabNamespace(7, "alex", true);

  private GitLabFixtures() {}

  public static TokenInfo expiringOn(LocalDate date) {
    return TokenInfo.expiring("alex", Set.of("api"), date);
  }
}
