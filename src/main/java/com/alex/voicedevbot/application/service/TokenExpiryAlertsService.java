package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.ConnectionView;
import com.alex.voicedevbot.application.port.in.TokenExpiryAlertsUseCase;
import com.alex.voicedevbot.application.port.out.ConnectionRepository;
import com.alex.voicedevbot.domain.TokenStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class TokenExpiryAlertsService implements TokenExpiryAlertsUseCase {

  private final ConnectionRepository connections;
  private final Clock clock;

  public TokenExpiryAlertsService(ConnectionRepository connections, Clock clock) {
    this.connections = Objects.requireNonNull(connections, "connections");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public List<ConnectionView> dueAlerts() {
    LocalDate today = LocalDate.now(clock);
    return connections.findAll().stream()
        .map(connection -> new ConnectionView(connection, connection.status(today)))
        .filter(view -> view.status() != TokenStatus.ACTIVE)
        .filter(view -> connections.claimExpiryAlert(view.id(), today))
        .toList();
  }
}
