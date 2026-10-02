package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.ConnectionProblem;
import com.alex.voicedevbot.application.port.in.ConnectionResult;
import com.alex.voicedevbot.application.port.in.ConnectionView;
import com.alex.voicedevbot.application.port.in.ManageConnectionsUseCase;
import com.alex.voicedevbot.application.port.out.CodeHost;
import com.alex.voicedevbot.application.port.out.ConnectionRepository;
import com.alex.voicedevbot.application.port.out.IntegrationException;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.AccessToken;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.TokenInfo;
import com.alex.voicedevbot.domain.TokenStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public class ManageConnectionsService implements ManageConnectionsUseCase {

  private final AccessPolicy accessPolicy;
  private final ConnectionRepository connections;
  private final CodeHost gitLab;
  private final Clock clock;

  public ManageConnectionsService(
      AccessPolicy accessPolicy, ConnectionRepository connections, CodeHost gitLab, Clock clock) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.connections = Objects.requireNonNull(connections, "connections");
    this.gitLab = Objects.requireNonNull(gitLab, "gitLab");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public ConnectionResult list(TelegramUserId user) {
    if (!accessPolicy.isAllowed(user)) {
      return new ConnectionResult.AccessDenied();
    }
    return new ConnectionResult.Listed(connections.findAll().stream().map(this::view).toList());
  }

  @Override
  public ConnectionResult show(TelegramUserId user, long connectionId) {
    if (!accessPolicy.isAllowed(user)) {
      return new ConnectionResult.AccessDenied();
    }
    return connections
        .find(connectionId)
        .<ConnectionResult>map(connection -> new ConnectionResult.Shown(view(connection)))
        .orElseGet(() -> new ConnectionResult.Rejected(ConnectionProblem.NOT_FOUND));
  }

  @Override
  public ConnectionResult add(TelegramUserId user, String address, String token) {
    if (!accessPolicy.isAllowed(user)) {
      return new ConnectionResult.AccessDenied();
    }
    ServerAddress parsed;
    try {
      parsed = ServerAddress.parse(address);
    } catch (IllegalArgumentException e) {
      return new ConnectionResult.Rejected(ConnectionProblem.INVALID_ADDRESS);
    }
    return verifyAndSave(parsed, token, Optional.empty());
  }

  @Override
  public ConnectionResult renew(TelegramUserId user, long connectionId, String token) {
    if (!accessPolicy.isAllowed(user)) {
      return new ConnectionResult.AccessDenied();
    }
    return connections
        .find(connectionId)
        .map(existing -> verifyAndSave(existing.address(), token, Optional.of(existing)))
        .orElseGet(() -> new ConnectionResult.Rejected(ConnectionProblem.NOT_FOUND));
  }

  @Override
  public ConnectionResult remove(TelegramUserId user, long connectionId) {
    if (!accessPolicy.isAllowed(user)) {
      return new ConnectionResult.AccessDenied();
    }
    connections.remove(connectionId);
    return new ConnectionResult.Removed();
  }

  private ConnectionResult verifyAndSave(
      ServerAddress address, String rawToken, Optional<ProviderConnection> renewing) {
    AccessToken token;
    try {
      token = new AccessToken(rawToken);
    } catch (IllegalArgumentException e) {
      return new ConnectionResult.Rejected(ConnectionProblem.INVALID_TOKEN_FORMAT);
    }
    TokenInfo info;
    try {
      info = gitLab.verify(address, token);
    } catch (IntegrationException e) {
      return new ConnectionResult.Rejected(ConnectionProblems.of(e));
    }
    Optional<ConnectionProblem> problem = problemWith(info, renewing);
    if (problem.isPresent()) {
      return new ConnectionResult.Rejected(problem.get());
    }
    return new ConnectionResult.Saved(view(connections.save(address, token, info)));
  }

  private Optional<ConnectionProblem> problemWith(
      TokenInfo info, Optional<ProviderConnection> renewing) {
    if (!info.hasRequiredScope()) {
      return Optional.of(ConnectionProblem.MISSING_SCOPE);
    }
    if (info.status(today()) == TokenStatus.EXPIRED) {
      return Optional.of(ConnectionProblem.TOKEN_EXPIRED);
    }
    boolean otherOwner =
        renewing.filter(old -> !old.info().owner().equals(info.owner())).isPresent();
    return otherOwner ? Optional.of(ConnectionProblem.OTHER_OWNER) : Optional.empty();
  }

  private ConnectionView view(ProviderConnection connection) {
    return new ConnectionView(connection, connection.status(today()));
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }
}
