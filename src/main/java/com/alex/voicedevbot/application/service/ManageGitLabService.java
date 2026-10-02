package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.ConnectionView;
import com.alex.voicedevbot.application.port.in.GitLabProblem;
import com.alex.voicedevbot.application.port.in.GitLabResult;
import com.alex.voicedevbot.application.port.in.ManageGitLabUseCase;
import com.alex.voicedevbot.application.port.out.GitLabApi;
import com.alex.voicedevbot.application.port.out.GitLabConnectionRepository;
import com.alex.voicedevbot.application.port.out.GitLabException;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.GitLabAddress;
import com.alex.voicedevbot.domain.GitLabConnection;
import com.alex.voicedevbot.domain.GitLabToken;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.TokenInfo;
import com.alex.voicedevbot.domain.TokenStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public class ManageGitLabService implements ManageGitLabUseCase {

  private final AccessPolicy accessPolicy;
  private final GitLabConnectionRepository connections;
  private final GitLabApi gitLab;
  private final Clock clock;

  public ManageGitLabService(
      AccessPolicy accessPolicy,
      GitLabConnectionRepository connections,
      GitLabApi gitLab,
      Clock clock) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.connections = Objects.requireNonNull(connections, "connections");
    this.gitLab = Objects.requireNonNull(gitLab, "gitLab");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public GitLabResult list(TelegramUserId user) {
    if (!accessPolicy.isAllowed(user)) {
      return new GitLabResult.AccessDenied();
    }
    return new GitLabResult.Listed(connections.findAll().stream().map(this::view).toList());
  }

  @Override
  public GitLabResult show(TelegramUserId user, long connectionId) {
    if (!accessPolicy.isAllowed(user)) {
      return new GitLabResult.AccessDenied();
    }
    return connections
        .find(connectionId)
        .<GitLabResult>map(connection -> new GitLabResult.Shown(view(connection)))
        .orElseGet(() -> new GitLabResult.Rejected(GitLabProblem.NOT_FOUND));
  }

  @Override
  public GitLabResult add(TelegramUserId user, String address, String token) {
    if (!accessPolicy.isAllowed(user)) {
      return new GitLabResult.AccessDenied();
    }
    GitLabAddress parsed;
    try {
      parsed = GitLabAddress.parse(address);
    } catch (IllegalArgumentException e) {
      return new GitLabResult.Rejected(GitLabProblem.INVALID_ADDRESS);
    }
    return verifyAndSave(parsed, token, Optional.empty());
  }

  @Override
  public GitLabResult renew(TelegramUserId user, long connectionId, String token) {
    if (!accessPolicy.isAllowed(user)) {
      return new GitLabResult.AccessDenied();
    }
    return connections
        .find(connectionId)
        .map(existing -> verifyAndSave(existing.address(), token, Optional.of(existing)))
        .orElseGet(() -> new GitLabResult.Rejected(GitLabProblem.NOT_FOUND));
  }

  @Override
  public GitLabResult remove(TelegramUserId user, long connectionId) {
    if (!accessPolicy.isAllowed(user)) {
      return new GitLabResult.AccessDenied();
    }
    connections.remove(connectionId);
    return new GitLabResult.Removed();
  }

  private GitLabResult verifyAndSave(
      GitLabAddress address, String rawToken, Optional<GitLabConnection> renewing) {
    GitLabToken token;
    try {
      token = new GitLabToken(rawToken);
    } catch (IllegalArgumentException e) {
      return new GitLabResult.Rejected(GitLabProblem.INVALID_TOKEN_FORMAT);
    }
    TokenInfo info;
    try {
      info = gitLab.verify(address, token);
    } catch (GitLabException e) {
      return new GitLabResult.Rejected(GitLabProblems.of(e));
    }
    Optional<GitLabProblem> problem = problemWith(info, renewing);
    if (problem.isPresent()) {
      return new GitLabResult.Rejected(problem.get());
    }
    return new GitLabResult.Saved(view(connections.save(address, token, info)));
  }

  private Optional<GitLabProblem> problemWith(TokenInfo info, Optional<GitLabConnection> renewing) {
    if (!info.hasRequiredScope()) {
      return Optional.of(GitLabProblem.MISSING_SCOPE);
    }
    if (info.status(today()) == TokenStatus.EXPIRED) {
      return Optional.of(GitLabProblem.TOKEN_EXPIRED);
    }
    boolean otherOwner =
        renewing.filter(old -> !old.info().owner().equals(info.owner())).isPresent();
    return otherOwner ? Optional.of(GitLabProblem.OTHER_OWNER) : Optional.empty();
  }

  private ConnectionView view(GitLabConnection connection) {
    return new ConnectionView(connection, connection.status(today()));
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }
}
