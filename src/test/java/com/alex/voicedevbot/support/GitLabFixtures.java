package com.alex.voicedevbot.support;

import com.alex.voicedevbot.application.service.Integrations;
import com.alex.voicedevbot.domain.AccessToken;
import com.alex.voicedevbot.domain.Namespace;
import com.alex.voicedevbot.domain.Provider;
import com.alex.voicedevbot.domain.Repo;
import com.alex.voicedevbot.domain.TokenInfo;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/** GitLab testlari uchun umumiy qiymatlar. */
public final class GitLabFixtures {

  public static final AccessToken TOKEN = new AccessToken("glpat-secretToken1234");
  public static final AccessToken NEW_TOKEN = new AccessToken("glpat-newSecretToken99");
  public static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
  public static final TokenInfo VALID =
      TokenInfo.expiring("alex", Set.of("api"), LocalDate.of(2027, 1, 1));
  public static final Repo REPO =
      new Repo(42, "alex/elt-imzo", URI.create("https://gitlab.com/alex/elt-imzo"));
  public static final Namespace PERSONAL = new Namespace(7, "alex", true);

  private GitLabFixtures() {}

  /** Faqat GitLab ulangan bot: kod va tasklar — shu soxta xizmat. */
  public static Integrations integrations(CodeHostAndTracker gitLab) {
    return new Integrations(Map.of(Provider.GITLAB, gitLab), Map.of(Provider.GITLAB, gitLab));
  }

  public static TokenInfo expiringOn(LocalDate date) {
    return TokenInfo.expiring("alex", Set.of("api"), date);
  }
}
