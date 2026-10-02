package com.alex.voicedevbot.application.service;

import static com.alex.voicedevbot.support.GitLabFixtures.NEW_TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.application.port.in.ConnectionProblem;
import com.alex.voicedevbot.application.port.in.ConnectionResult;
import com.alex.voicedevbot.application.port.out.IntegrationException;
import com.alex.voicedevbot.application.port.out.IntegrationException.Reason;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.Provider;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.TokenInfo;
import com.alex.voicedevbot.domain.TokenStatus;
import com.alex.voicedevbot.support.CodeHostAndTracker;
import com.alex.voicedevbot.support.GitLabFixtures;
import com.alex.voicedevbot.support.InMemoryConnectionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ManageConnectionsServiceTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ServerAddress SELF_HOSTED = ServerAddress.parse("git.example.uz");

  private final InMemoryConnectionRepository connections = new InMemoryConnectionRepository();
  private final CodeHostAndTracker api = mock(CodeHostAndTracker.class);
  private final ManageConnectionsService service =
      new ManageConnectionsService(
          new AccessPolicy(Set.of(USER)),
          connections,
          GitLabFixtures.integrations(api),
          Clock.fixed(
              GitLabFixtures.TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

  @Test
  void should_verify_and_save_connection_when_token_is_valid() {
    when(api.verify(SELF_HOSTED, TOKEN)).thenReturn(VALID);

    ConnectionResult result = service.add(USER, Provider.GITLAB, "git.example.uz/", TOKEN.value());

    assertThat(result)
        .isInstanceOfSatisfying(
            ConnectionResult.Saved.class,
            saved -> {
              assertThat(saved.connection().connection())
                  .isEqualTo(new ProviderConnection(1, Provider.GITLAB, SELF_HOSTED, TOKEN, VALID));
              assertThat(saved.connection().status()).isEqualTo(TokenStatus.ACTIVE);
            });
    assertThat(connections.findAll()).hasSize(1);
  }

  @Test
  void should_reject_invalid_address_and_token_format_without_calling_gitlab() {
    assertThat(service.add(USER, Provider.GITLAB, "ftp://x", TOKEN.value()))
        .isEqualTo(new ConnectionResult.Rejected(ConnectionProblem.INVALID_ADDRESS));
    assertThat(service.add(USER, Provider.GITLAB, "gitlab.com", "bad token"))
        .isEqualTo(new ConnectionResult.Rejected(ConnectionProblem.INVALID_TOKEN_FORMAT));
    verifyNoInteractions(api);
  }

  @ParameterizedTest
  @CsvSource({
    "UNAUTHORIZED, TOKEN_REJECTED",
    "FORBIDDEN, FORBIDDEN",
    "NOT_FOUND, NOT_FOUND",
    "UNAVAILABLE, UNREACHABLE"
  })
  void should_explain_why_gitlab_rejected_token(Reason reason, ConnectionProblem problem) {
    when(api.verify(any(), any())).thenThrow(new IntegrationException(reason, "GET /user", null));

    assertThat(service.add(USER, Provider.GITLAB, "gitlab.com", TOKEN.value()))
        .isEqualTo(new ConnectionResult.Rejected(problem));
    assertThat(connections.findAll()).isEmpty();
  }

  @Test
  void should_reject_token_without_api_scope_or_already_expired() {
    when(api.verify(any(), any()))
        .thenReturn(TokenInfo.withoutExpiry("alex", Set.of("read_api")))
        .thenReturn(GitLabFixtures.expiringOn(GitLabFixtures.TODAY));

    assertThat(service.add(USER, Provider.GITLAB, "gitlab.com", TOKEN.value()))
        .isEqualTo(new ConnectionResult.Rejected(ConnectionProblem.MISSING_SCOPE));
    assertThat(service.add(USER, Provider.GITLAB, "gitlab.com", TOKEN.value()))
        .isEqualTo(new ConnectionResult.Rejected(ConnectionProblem.TOKEN_EXPIRED));
    assertThat(connections.findAll()).isEmpty();
  }

  @Test
  void should_renew_token_of_same_owner_and_keep_connection_id() {
    ProviderConnection existing = connections.save(Provider.GITLAB, SELF_HOSTED, TOKEN, VALID);
    TokenInfo renewed = GitLabFixtures.expiringOn(LocalDate.of(2027, 6, 1));
    when(api.verify(SELF_HOSTED, NEW_TOKEN)).thenReturn(renewed);

    ConnectionResult result = service.renew(USER, existing.id(), NEW_TOKEN.value());

    assertThat(result)
        .isEqualTo(
            new ConnectionResult.Saved(
                new com.alex.voicedevbot.application.port.in.ConnectionView(
                    new ProviderConnection(
                        existing.id(), Provider.GITLAB, SELF_HOSTED, NEW_TOKEN, renewed),
                    TokenStatus.ACTIVE)));
  }

  @Test
  void should_refuse_renewal_with_token_of_another_user() {
    ProviderConnection existing = connections.save(Provider.GITLAB, SELF_HOSTED, TOKEN, VALID);
    when(api.verify(any(), any())).thenReturn(TokenInfo.withoutExpiry("boshqa", Set.of("api")));

    assertThat(service.renew(USER, existing.id(), NEW_TOKEN.value()))
        .isEqualTo(new ConnectionResult.Rejected(ConnectionProblem.OTHER_OWNER));
    assertThat(connections.find(existing.id()).orElseThrow().token()).isEqualTo(TOKEN);
  }

  @Test
  void should_list_show_and_remove_connections() {
    ProviderConnection saved =
        connections.save(
            Provider.GITLAB,
            ServerAddress.GITLAB_COM,
            TOKEN,
            GitLabFixtures.expiringOn(LocalDate.of(2026, 10, 5)));

    assertThat(service.list(USER))
        .isInstanceOfSatisfying(
            ConnectionResult.Listed.class,
            listed ->
                assertThat(listed.connections().getFirst().status())
                    .isEqualTo(TokenStatus.EXPIRING_SOON));
    assertThat(service.show(USER, saved.id())).isInstanceOf(ConnectionResult.Shown.class);
    assertThat(service.remove(USER, saved.id())).isInstanceOf(ConnectionResult.Removed.class);
    assertThat(service.show(USER, saved.id()))
        .isEqualTo(new ConnectionResult.Rejected(ConnectionProblem.NOT_FOUND));
    assertThat(service.renew(USER, saved.id(), TOKEN.value()))
        .isEqualTo(new ConnectionResult.Rejected(ConnectionProblem.NOT_FOUND));
  }

  @Test
  void should_deny_stranger_everything() {
    ProviderConnection saved = connections.save(Provider.GITLAB, SELF_HOSTED, TOKEN, VALID);

    assertThat(service.list(STRANGER)).isInstanceOf(ConnectionResult.AccessDenied.class);
    assertThat(service.show(STRANGER, saved.id()))
        .isInstanceOf(ConnectionResult.AccessDenied.class);
    assertThat(service.add(STRANGER, Provider.GITLAB, "gitlab.com", TOKEN.value()))
        .isInstanceOf(ConnectionResult.AccessDenied.class);
    assertThat(service.renew(STRANGER, saved.id(), TOKEN.value()))
        .isInstanceOf(ConnectionResult.AccessDenied.class);
    assertThat(service.remove(STRANGER, saved.id()))
        .isInstanceOf(ConnectionResult.AccessDenied.class);
    verifyNoInteractions(api);
    assertThat(connections.findAll()).hasSize(1);
  }

  @Test
  void should_reject_provider_without_adapter_and_list_supported_ones() {
    assertThat(service.add(USER, Provider.GITHUB, "", TOKEN.value()))
        .isEqualTo(new ConnectionResult.Rejected(ConnectionProblem.UNSUPPORTED));
    assertThat(service.list(USER))
        .isInstanceOfSatisfying(
            ConnectionResult.Listed.class,
            listed -> assertThat(listed.providers()).containsExactly(Provider.GITLAB));
    verifyNoInteractions(api);
  }

  @Test
  void should_use_default_server_and_refuse_other_address_for_cloud_only_provider() {
    CodeHostAndTracker gitHub = mock(CodeHostAndTracker.class);
    ManageConnectionsService withGitHub =
        new ManageConnectionsService(
            new AccessPolicy(Set.of(USER)),
            connections,
            new Integrations(
                java.util.Map.of(Provider.GITLAB, api, Provider.GITHUB, gitHub),
                java.util.Map.of(Provider.GITLAB, api, Provider.GITHUB, gitHub)),
            Clock.fixed(
                GitLabFixtures.TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    TokenInfo noScopes = TokenInfo.withoutExpiry("alex", Set.of());
    when(gitHub.verify(ServerAddress.GITHUB_COM, TOKEN)).thenReturn(noScopes);

    ConnectionResult saved = withGitHub.add(USER, Provider.GITHUB, " ", TOKEN.value());
    ConnectionResult elsewhere =
        withGitHub.add(USER, Provider.GITHUB, "git.example.uz", TOKEN.value());

    assertThat(saved)
        .isInstanceOfSatisfying(
            ConnectionResult.Saved.class,
            result ->
                assertThat(result.connection().connection().provider()).isEqualTo(Provider.GITHUB));
    assertThat(elsewhere)
        .isEqualTo(new ConnectionResult.Rejected(ConnectionProblem.INVALID_ADDRESS));
    verifyNoInteractions(api);
  }

  @Test
  void should_renew_token_with_the_connection_provider() {
    CodeHostAndTracker gitHub = mock(CodeHostAndTracker.class);
    ManageConnectionsService withGitHub =
        new ManageConnectionsService(
            new AccessPolicy(Set.of(USER)),
            connections,
            new Integrations(
                java.util.Map.of(Provider.GITLAB, api, Provider.GITHUB, gitHub),
                java.util.Map.of()),
            Clock.fixed(
                GitLabFixtures.TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    ProviderConnection existing =
        connections.save(Provider.GITHUB, ServerAddress.GITHUB_COM, TOKEN, VALID);
    when(gitHub.verify(ServerAddress.GITHUB_COM, NEW_TOKEN)).thenReturn(VALID);

    assertThat(withGitHub.renew(USER, existing.id(), NEW_TOKEN.value()))
        .isInstanceOf(ConnectionResult.Saved.class);
    verifyNoInteractions(api);
  }
}
