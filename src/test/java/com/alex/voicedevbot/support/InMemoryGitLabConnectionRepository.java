package com.alex.voicedevbot.support;

import com.alex.voicedevbot.application.port.out.GitLabConnectionRepository;
import com.alex.voicedevbot.domain.GitLabAddress;
import com.alex.voicedevbot.domain.GitLabConnection;
import com.alex.voicedevbot.domain.GitLabToken;
import com.alex.voicedevbot.domain.TokenInfo;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link GitLabConnectionRepository} xotirada: server + egasi bo'yicha upsert. */
public class InMemoryGitLabConnectionRepository implements GitLabConnectionRepository {

  private final Map<Long, GitLabConnection> connections = new HashMap<>();
  private final Map<Long, LocalDate> alertedOn = new HashMap<>();
  private long nextId = 1;

  @Override
  public GitLabConnection save(GitLabAddress address, GitLabToken token, TokenInfo info) {
    long id =
        connections.values().stream()
            .filter(c -> c.address().equals(address) && c.info().owner().equals(info.owner()))
            .map(GitLabConnection::id)
            .findFirst()
            .orElseGet(() -> nextId++);
    GitLabConnection saved = new GitLabConnection(id, address, token, info);
    connections.put(id, saved);
    alertedOn.remove(id);
    return saved;
  }

  @Override
  public List<GitLabConnection> findAll() {
    return connections.values().stream()
        .sorted(
            Comparator.comparing((GitLabConnection c) -> c.address().toString())
                .thenComparing(c -> c.info().owner()))
        .toList();
  }

  @Override
  public Optional<GitLabConnection> find(long id) {
    return Optional.ofNullable(connections.get(id));
  }

  @Override
  public void remove(long id) {
    connections.remove(id);
  }

  @Override
  public boolean claimExpiryAlert(long id, LocalDate day) {
    return !day.equals(alertedOn.put(id, day));
  }
}
