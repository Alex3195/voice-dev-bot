package com.alex.voicedevbot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RepoUrlTest {

  private static final ServerAddress SELF_HOSTED = ServerAddress.parse("https://git.example.uz");

  @ParameterizedTest
  @CsvSource({
    "https://git.example.uz/akfa/backend/elt-imzo, akfa/backend/elt-imzo",
    "https://git.example.uz/akfa/elt-imzo.git, akfa/elt-imzo",
    "https://git.example.uz/akfa/elt-imzo/, akfa/elt-imzo",
    "https://git.example.uz/akfa/elt-imzo/-/issues/5, akfa/elt-imzo",
    "https://git.example.uz/akfa/elt-imzo/-/tree/main?ref_type=heads, akfa/elt-imzo",
    "git.example.uz/akfa/elt-imzo, akfa/elt-imzo",
    "git@git.example.uz:akfa/elt-imzo.git, akfa/elt-imzo",
    "ssh://git@git.example.uz/akfa/elt-imzo.git, akfa/elt-imzo",
    "HTTPS://GIT.EXAMPLE.UZ/akfa/elt-imzo, akfa/elt-imzo"
  })
  void should_find_repo_path_on_gitlab_server(String url, String path) {
    assertThat(RepoUrl.parse(url).repoPath(SELF_HOSTED, Provider.GITLAB)).contains(path);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://github.com/alex/elt-imzo",
        "https://github.com/alex/elt-imzo.git",
        "https://github.com/alex/elt-imzo/tree/main/docs",
        "https://github.com/alex/elt-imzo/issues/3",
        "git@github.com:alex/elt-imzo.git"
      })
  void should_keep_only_owner_and_name_on_github(String url) {
    assertThat(RepoUrl.parse(url).repoPath(ServerAddress.GITHUB_COM, Provider.GITHUB))
        .contains("alex/elt-imzo");
  }

  @ParameterizedTest
  @CsvSource({
    "http://10.0.0.5/gitlab/akfa/elt-imzo, http://10.0.0.5/gitlab, akfa/elt-imzo",
    "http://10.0.0.5:8080/akfa/elt-imzo, http://10.0.0.5:8080, akfa/elt-imzo"
  })
  void should_strip_server_path_and_port(String url, String server, String path) {
    assertThat(RepoUrl.parse(url).repoPath(ServerAddress.parse(server), Provider.GITLAB))
        .contains(path);
  }

  @ParameterizedTest
  @CsvSource({
    "https://gitlab.com/akfa/elt-imzo, https://git.example.uz",
    "http://10.0.0.5/akfa/elt-imzo, http://10.0.0.5/gitlab",
    "http://10.0.0.5/gitlab/elt-imzo, http://10.0.0.5/gitlab",
    "http://10.0.0.5:8080/akfa/elt-imzo, http://10.0.0.5"
  })
  void should_not_match_other_server(String url, String server) {
    assertThat(RepoUrl.parse(url).repoPath(ServerAddress.parse(server), Provider.GITLAB)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "git@git.example.uz:akfa/elt-imzo.git, https://git.example.uz",
    "http://10.0.0.5:8080/gitlab/a/b, http://10.0.0.5:8080"
  })
  void should_keep_server_without_path(String url, String server) {
    assertThat(RepoUrl.parse(url).server()).isEqualTo(ServerAddress.parse(server));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "  ",
        "https://github.com/alex",
        "https://github.com",
        "ftp://github.com/alex/elt-imzo",
        "https://user:pass@github.com/alex/elt-imzo",
        "https://git.example.uz/-/akfa",
        "not a url at all"
      })
  void should_reject_url_without_owner_and_name(String url) {
    assertThatThrownBy(() -> RepoUrl.parse(url)).isInstanceOf(IllegalArgumentException.class);
  }
}
