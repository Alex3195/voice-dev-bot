package com.alex.voicedevbot.application.service;

import static com.alex.voicedevbot.support.GitLabFixtures.TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.alex.voicedevbot.application.port.out.IntegrationException;
import com.alex.voicedevbot.domain.Provider;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.support.CodeHostAndTracker;
import com.alex.voicedevbot.support.GitLabFixtures;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IntegrationsTest {

  private final CodeHostAndTracker gitLab = mock(CodeHostAndTracker.class);
  private final CodeHostAndTracker gitHub = mock(CodeHostAndTracker.class);

  private static ProviderConnection connection(Provider provider) {
    return new ProviderConnection(1, provider, provider.defaultAddress(), TOKEN, VALID);
  }

  @Test
  void should_pick_adapter_by_connection_provider() {
    Integrations integrations =
        new Integrations(
            Map.of(Provider.GITLAB, gitLab, Provider.GITHUB, gitHub),
            Map.of(Provider.GITLAB, gitLab, Provider.GITHUB, gitHub));

    assertThat(integrations.codeHost(connection(Provider.GITHUB))).isSameAs(gitHub);
    assertThat(integrations.issueTracker(connection(Provider.GITLAB))).isSameAs(gitLab);
    assertThat(integrations.supported()).containsExactly(Provider.GITLAB, Provider.GITHUB);
  }

  @Test
  void should_support_only_providers_with_code_adapter() {
    Integrations integrations = GitLabFixtures.integrations(gitLab);

    assertThat(integrations.supported()).containsExactly(Provider.GITLAB);
    assertThat(integrations.supports(Provider.GITHUB)).isFalse();
  }

  @Test
  void should_fail_as_unavailable_for_connection_without_adapter() {
    Integrations integrations = GitLabFixtures.integrations(gitLab);

    assertThatThrownBy(() -> integrations.codeHost(connection(Provider.GITHUB)))
        .isInstanceOfSatisfying(
            IntegrationException.class,
            e -> assertThat(e.reason()).isEqualTo(IntegrationException.Reason.UNAVAILABLE));
    assertThatThrownBy(() -> integrations.issueTracker(connection(Provider.GITHUB)))
        .isInstanceOf(IntegrationException.class);
  }
}
