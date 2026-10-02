package com.alex.voicedevbot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConnectionDomainTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

  @Test
  void should_mask_token_everywhere_it_can_be_printed() {
    AccessToken token = new AccessToken(" glpat-AbCdEfGh1234a1b2 ");

    assertThat(token.value()).isEqualTo("glpat-AbCdEfGh1234a1b2");
    assertThat(token.masked()).isEqualTo("glpat-…a1b2");
    assertThat(token.toString()).isEqualTo("AccessToken[glpat-…a1b2]").doesNotContain("AbCd");
    assertThat(new AccessToken("abcdefgh1234").masked()).isEqualTo("…1234");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "short", "has space inside", "glpat-ab\ncd1234"})
  void should_reject_token_with_invalid_format(String value) {
    assertThatThrownBy(() -> new AccessToken(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "gitlab.com, https://gitlab.com, gitlab.com",
    "https://gitlab.com/, https://gitlab.com, gitlab.com",
    "http://100.64.0.5:8080/gitlab//, http://100.64.0.5:8080/gitlab, 100.64.0.5:8080/gitlab",
    "  git.example.uz , https://git.example.uz, git.example.uz"
  })
  void should_normalize_gitlab_address(String input, String uri, String label) {
    ServerAddress address = ServerAddress.parse(input);

    assertThat(address.uri()).isEqualTo(URI.create(uri));
    assertThat(address.label()).isEqualTo(label);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "ftp://gitlab.com",
        "https://user:pass@gitlab.com",
        "https://x?y=1",
        "http://"
      })
  void should_reject_invalid_gitlab_address(String input) {
    assertThatThrownBy(() -> ServerAddress.parse(input))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "2026-12-01, ACTIVE",
    "2026-10-10, ACTIVE",
    "2026-10-09, EXPIRING_SOON",
    "2026-10-03, EXPIRING_SOON",
    "2026-10-02, EXPIRED",
    "2026-09-01, EXPIRED"
  })
  void should_tell_token_status_by_expiry_date(LocalDate expiresAt, TokenStatus status) {
    TokenInfo info = TokenInfo.expiring("alex", Set.of("api"), expiresAt);

    assertThat(info.status(TODAY)).isEqualTo(status);
  }

  @Test
  void should_treat_token_without_expiry_as_active() {
    TokenInfo info = TokenInfo.withoutExpiry("alex", Set.of("read_api"));

    assertThat(info.status(TODAY)).isEqualTo(TokenStatus.ACTIVE);
    assertThat(info.expiresAt()).isEmpty();
    assertThat(info.hasScope("api")).isFalse();
    assertThat(info.hasScope("read_api")).isTrue();
  }

  @Test
  void should_compare_token_info_by_value() {
    TokenInfo info = TokenInfo.expiring("alex", Set.of("api"), TODAY);

    assertThat(info)
        .isEqualTo(TokenInfo.expiring("alex", Set.of("api"), TODAY))
        .hasSameHashCodeAs(TokenInfo.expiring("alex", Set.of("api"), TODAY))
        .isNotEqualTo(TokenInfo.withoutExpiry("alex", Set.of("api")));
    assertThat(info.toString()).contains("alex").contains("2026-10-02");
    assertThatThrownBy(() -> TokenInfo.withoutExpiry(" ", Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_label_connection_and_reject_blank_repo_or_namespace() {
    ProviderConnection connection =
        new ProviderConnection(
            1,
            Provider.GITLAB,
            ServerAddress.GITLAB_COM,
            new AccessToken("glpat-12345678"),
            TokenInfo.withoutExpiry("alex", Set.of("api")));

    assertThat(connection.label()).isEqualTo("gitlab.com · @alex");
    assertThat(connection.status(TODAY)).isEqualTo(TokenStatus.ACTIVE);
    assertThatThrownBy(() -> new Repo(1, " ", URI.create("https://gitlab.com/x")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Namespace(1, "", true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_describe_providers() {
    assertThat(Provider.GITLAB.defaultAddress()).isEqualTo(ServerAddress.GITLAB_COM);
    assertThat(Provider.GITLAB.selfHosted()).isTrue();
    assertThat(Provider.GITLAB.requiredScope()).contains("api");
    assertThat(Provider.GITHUB.defaultAddress().label()).isEqualTo("github.com");
    assertThat(Provider.GITHUB.selfHosted()).isFalse();
    assertThat(Provider.GITHUB.displayName()).isEqualTo("GitHub");
  }

  @ParameterizedTest
  @CsvSource({"GITLAB, api, true", "GITLAB, read_api, false", "GITHUB, read_api, true"})
  void should_check_scope_required_by_provider(Provider provider, String scope, boolean enough) {
    ProviderConnection connection =
        new ProviderConnection(
            1,
            provider,
            provider.defaultAddress(),
            new AccessToken("glpat-12345678"),
            TokenInfo.withoutExpiry("alex", Set.of(scope)));

    assertThat(connection.hasRequiredScope()).isEqualTo(enough);
  }

  @Test
  void should_require_provider_for_connection() {
    assertThatThrownBy(
            () ->
                new ProviderConnection(
                    1,
                    null,
                    ServerAddress.GITLAB_COM,
                    new AccessToken("glpat-12345678"),
                    TokenInfo.withoutExpiry("alex", Set.of("api"))))
        .isInstanceOf(NullPointerException.class);
  }
}
