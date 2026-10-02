package com.alex.voicedevbot.application.service;

import static com.alex.voicedevbot.support.GitLabFixtures.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.application.port.in.ConnectionView;
import com.alex.voicedevbot.domain.Provider;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.TokenStatus;
import com.alex.voicedevbot.support.GitLabFixtures;
import com.alex.voicedevbot.support.InMemoryConnectionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TokenExpiryAlertsServiceTest {

  private final InMemoryConnectionRepository connections = new InMemoryConnectionRepository();

  private TokenExpiryAlertsService serviceOn(LocalDate day) {
    return new TokenExpiryAlertsService(
        connections, Clock.fixed(day.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
  }

  @Test
  void should_alert_expiring_and_expired_tokens_once_per_day() {
    connections.save(Provider.GITLAB, ServerAddress.parse("a.uz"), TOKEN, GitLabFixtures.VALID);
    connections.save(
        Provider.GITLAB,
        ServerAddress.parse("b.uz"),
        TOKEN,
        GitLabFixtures.expiringOn(LocalDate.of(2026, 10, 5)));
    connections.save(
        Provider.GITLAB,
        ServerAddress.parse("c.uz"),
        TOKEN,
        GitLabFixtures.expiringOn(LocalDate.of(2026, 9, 1)));

    var first = serviceOn(GitLabFixtures.TODAY).dueAlerts();
    var sameDay = serviceOn(GitLabFixtures.TODAY).dueAlerts();
    var nextDay = serviceOn(GitLabFixtures.TODAY.plusDays(1)).dueAlerts();

    assertThat(first)
        .extracting(ConnectionView::status)
        .containsExactly(TokenStatus.EXPIRING_SOON, TokenStatus.EXPIRED);
    assertThat(sameDay).isEmpty();
    assertThat(nextDay).hasSize(2);
  }
}
