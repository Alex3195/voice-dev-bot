package com.alex.voicedevbot.application.service;

import static com.alex.voicedevbot.support.GitLabFixtures.NEW_TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.application.port.in.GitLabProblem;
import com.alex.voicedevbot.application.port.in.GitLabResult;
import com.alex.voicedevbot.application.port.out.GitLabApi;
import com.alex.voicedevbot.application.port.out.GitLabException;
import com.alex.voicedevbot.application.port.out.GitLabException.Reason;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.GitLabAddress;
import com.alex.voicedevbot.domain.GitLabConnection;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.TokenInfo;
import com.alex.voicedevbot.domain.TokenStatus;
import com.alex.voicedevbot.support.GitLabFixtures;
import com.alex.voicedevbot.support.InMemoryGitLabConnectionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ManageGitLabServiceTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final GitLabAddress SELF_HOSTED = GitLabAddress.parse("git.example.uz");

  private final InMemoryGitLabConnectionRepository connections =
      new InMemoryGitLabConnectionRepository();
  private final GitLabApi api = mock(GitLabApi.class);
  private final ManageGitLabService service =
      new ManageGitLabService(
          new AccessPolicy(Set.of(USER)),
          connections,
          api,
          Clock.fixed(
              GitLabFixtures.TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

  @Test
  void should_verify_and_save_connection_when_token_is_valid() {
    when(api.verify(SELF_HOSTED, TOKEN)).thenReturn(VALID);

    GitLabResult result = service.add(USER, "git.example.uz/", TOKEN.value());

    assertThat(result)
        .isInstanceOfSatisfying(
            GitLabResult.Saved.class,
            saved -> {
              assertThat(saved.connection().connection())
                  .isEqualTo(new GitLabConnection(1, SELF_HOSTED, TOKEN, VALID));
              assertThat(saved.connection().status()).isEqualTo(TokenStatus.ACTIVE);
            });
    assertThat(connections.findAll()).hasSize(1);
  }

  @Test
  void should_reject_invalid_address_and_token_format_without_calling_gitlab() {
    assertThat(service.add(USER, "ftp://x", TOKEN.value()))
        .isEqualTo(new GitLabResult.Rejected(GitLabProblem.INVALID_ADDRESS));
    assertThat(service.add(USER, "gitlab.com", "bad token"))
        .isEqualTo(new GitLabResult.Rejected(GitLabProblem.INVALID_TOKEN_FORMAT));
    verifyNoInteractions(api);
  }

  @ParameterizedTest
  @CsvSource({
    "UNAUTHORIZED, TOKEN_REJECTED",
    "FORBIDDEN, FORBIDDEN",
    "NOT_FOUND, NOT_FOUND",
    "UNAVAILABLE, UNREACHABLE"
  })
  void should_explain_why_gitlab_rejected_token(Reason reason, GitLabProblem problem) {
    when(api.verify(any(), any())).thenThrow(new GitLabException(reason, "GET /user", null));

    assertThat(service.add(USER, "gitlab.com", TOKEN.value()))
        .isEqualTo(new GitLabResult.Rejected(problem));
    assertThat(connections.findAll()).isEmpty();
  }

  @Test
  void should_reject_token_without_api_scope_or_already_expired() {
    when(api.verify(any(), any()))
        .thenReturn(TokenInfo.withoutExpiry("alex", Set.of("read_api")))
        .thenReturn(GitLabFixtures.expiringOn(GitLabFixtures.TODAY));

    assertThat(service.add(USER, "gitlab.com", TOKEN.value()))
        .isEqualTo(new GitLabResult.Rejected(GitLabProblem.MISSING_SCOPE));
    assertThat(service.add(USER, "gitlab.com", TOKEN.value()))
        .isEqualTo(new GitLabResult.Rejected(GitLabProblem.TOKEN_EXPIRED));
    assertThat(connections.findAll()).isEmpty();
  }

  @Test
  void should_renew_token_of_same_owner_and_keep_connection_id() {
    GitLabConnection existing = connections.save(SELF_HOSTED, TOKEN, VALID);
    TokenInfo renewed = GitLabFixtures.expiringOn(LocalDate.of(2027, 6, 1));
    when(api.verify(SELF_HOSTED, NEW_TOKEN)).thenReturn(renewed);

    GitLabResult result = service.renew(USER, existing.id(), NEW_TOKEN.value());

    assertThat(result)
        .isEqualTo(
            new GitLabResult.Saved(
                new com.alex.voicedevbot.application.port.in.ConnectionView(
                    new GitLabConnection(existing.id(), SELF_HOSTED, NEW_TOKEN, renewed),
                    TokenStatus.ACTIVE)));
  }

  @Test
  void should_refuse_renewal_with_token_of_another_user() {
    GitLabConnection existing = connections.save(SELF_HOSTED, TOKEN, VALID);
    when(api.verify(any(), any())).thenReturn(TokenInfo.withoutExpiry("boshqa", Set.of("api")));

    assertThat(service.renew(USER, existing.id(), NEW_TOKEN.value()))
        .isEqualTo(new GitLabResult.Rejected(GitLabProblem.OTHER_OWNER));
    assertThat(connections.find(existing.id()).orElseThrow().token()).isEqualTo(TOKEN);
  }

  @Test
  void should_list_show_and_remove_connections() {
    GitLabConnection saved =
        connections.save(
            GitLabAddress.GITLAB_COM, TOKEN, GitLabFixtures.expiringOn(LocalDate.of(2026, 10, 5)));

    assertThat(service.list(USER))
        .isInstanceOfSatisfying(
            GitLabResult.Listed.class,
            listed ->
                assertThat(listed.connections().getFirst().status())
                    .isEqualTo(TokenStatus.EXPIRING_SOON));
    assertThat(service.show(USER, saved.id())).isInstanceOf(GitLabResult.Shown.class);
    assertThat(service.remove(USER, saved.id())).isInstanceOf(GitLabResult.Removed.class);
    assertThat(service.show(USER, saved.id()))
        .isEqualTo(new GitLabResult.Rejected(GitLabProblem.NOT_FOUND));
    assertThat(service.renew(USER, saved.id(), TOKEN.value()))
        .isEqualTo(new GitLabResult.Rejected(GitLabProblem.NOT_FOUND));
  }

  @Test
  void should_deny_stranger_everything() {
    GitLabConnection saved = connections.save(SELF_HOSTED, TOKEN, VALID);

    assertThat(service.list(STRANGER)).isInstanceOf(GitLabResult.AccessDenied.class);
    assertThat(service.show(STRANGER, saved.id())).isInstanceOf(GitLabResult.AccessDenied.class);
    assertThat(service.add(STRANGER, "gitlab.com", TOKEN.value()))
        .isInstanceOf(GitLabResult.AccessDenied.class);
    assertThat(service.renew(STRANGER, saved.id(), TOKEN.value()))
        .isInstanceOf(GitLabResult.AccessDenied.class);
    assertThat(service.remove(STRANGER, saved.id())).isInstanceOf(GitLabResult.AccessDenied.class);
    verifyNoInteractions(api);
    assertThat(connections.findAll()).hasSize(1);
  }
}
