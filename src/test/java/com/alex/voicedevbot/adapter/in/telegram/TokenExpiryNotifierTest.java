package com.alex.voicedevbot.adapter.in.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.application.port.in.ConnectionView;
import com.alex.voicedevbot.application.port.in.TokenExpiryAlertsUseCase;
import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.domain.GitLabAddress;
import com.alex.voicedevbot.domain.GitLabConnection;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.TokenStatus;
import com.alex.voicedevbot.support.GitLabFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenExpiryNotifierTest {

  private static final TelegramUserId ALEX = new TelegramUserId(1L);
  private static final TelegramUserId TEAMMATE = new TelegramUserId(2L);

  private final TokenExpiryAlertsUseCase alerts = mock(TokenExpiryAlertsUseCase.class);
  private final VoiceDevBot bot = mock(VoiceDevBot.class);
  private final TokenExpiryNotifier notifier =
      new TokenExpiryNotifier(alerts, bot, Set.of(ALEX, TEAMMATE));

  @Test
  void should_send_renewal_alert_to_every_whitelisted_user() {
    GitLabConnection connection =
        new GitLabConnection(
            5,
            GitLabAddress.GITLAB_COM,
            GitLabFixtures.TOKEN,
            GitLabFixtures.expiringOn(LocalDate.of(2026, 10, 5)));
    when(alerts.dueAlerts())
        .thenReturn(List.of(new ConnectionView(connection, TokenStatus.EXPIRING_SOON)));

    notifier.run();

    ArgumentCaptor<Screen> alert = ArgumentCaptor.forClass(Screen.class);
    verify(bot).notify(org.mockito.ArgumentMatchers.eq(ALEX), alert.capture());
    verify(bot).notify(org.mockito.ArgumentMatchers.eq(TEAMMATE), any());
    assertThat(alert.getValue().html())
        .startsWith("⚠️ <b>GitLab tokeni tugayapti</b>")
        .contains("2026-10-05 da tugaydi");
    assertThat(alert.getValue().rows().getFirst().getFirst().action())
        .isEqualTo(Actions.GITLAB_RENEW + 5);
  }

  @Test
  void should_survive_failures_so_next_run_still_happens() {
    when(alerts.dueAlerts()).thenThrow(new StorageException("db down", null));

    notifier.run();

    verifyNoInteractions(bot);
  }
}
