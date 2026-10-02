package com.alex.voicedevbot.adapter.out.persistence;

import com.alex.voicedevbot.application.port.out.GitLabConnectionRepository;
import com.alex.voicedevbot.domain.GitLabAddress;
import com.alex.voicedevbot.domain.GitLabConnection;
import com.alex.voicedevbot.domain.GitLabToken;
import com.alex.voicedevbot.domain.TokenInfo;
import java.sql.Array;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;

/** GitLab ulanishlari PostgreSQL'da; token {@link TokenCipher} bilan shifrlangan. */
public class JdbcGitLabConnectionRepository implements GitLabConnectionRepository {

  private static final String SELECT =
      "select id, base_url, username, token_encrypted, scopes, expires_at from gitlab_connection";
  private static final String UPSERT =
      """
      insert into gitlab_connection (base_url, username, token_encrypted, scopes, expires_at)
      values (?, ?, ?, ?, ?)
      on conflict (base_url, username) do update
      set token_encrypted = excluded.token_encrypted, scopes = excluded.scopes,
          expires_at = excluded.expires_at, last_alerted_on = null, verified_at = now()
      returning id
      """;

  private final Jdbc jdbc;
  private final TokenCipher cipher;

  public JdbcGitLabConnectionRepository(DataSource dataSource, TokenCipher cipher) {
    this.jdbc = new Jdbc(dataSource);
    this.cipher = Objects.requireNonNull(cipher, "cipher");
  }

  @Override
  public GitLabConnection save(GitLabAddress address, GitLabToken token, TokenInfo info) {
    long id =
        jdbc.query(
            "save GitLab connection",
            connection -> {
              try (PreparedStatement upsert = connection.prepareStatement(UPSERT)) {
                bind(connection, upsert, address, token, info);
                try (ResultSet rows = upsert.executeQuery()) {
                  rows.next();
                  return rows.getLong("id");
                }
              }
            });
    return new GitLabConnection(id, address, token, info);
  }

  @Override
  public List<GitLabConnection> findAll() {
    return jdbc.query(
        "list GitLab connections",
        connection -> {
          try (PreparedStatement select =
              connection.prepareStatement(SELECT + " order by base_url, username")) {
            return read(select);
          }
        });
  }

  @Override
  public Optional<GitLabConnection> find(long id) {
    return jdbc.query(
        "find GitLab connection",
        connection -> {
          try (PreparedStatement select = connection.prepareStatement(SELECT + " where id = ?")) {
            select.setLong(1, id);
            return read(select).stream().findFirst();
          }
        });
  }

  @Override
  public void remove(long id) {
    jdbc.query(
        "remove GitLab connection",
        connection -> {
          try (PreparedStatement delete =
              connection.prepareStatement("delete from gitlab_connection where id = ?")) {
            delete.setLong(1, id);
            return delete.executeUpdate();
          }
        });
  }

  @Override
  public boolean claimExpiryAlert(long id, LocalDate day) {
    return jdbc.query(
        "claim expiry alert",
        connection -> {
          try (PreparedStatement update =
              connection.prepareStatement(
                  "update gitlab_connection set last_alerted_on = ?"
                      + " where id = ? and last_alerted_on is distinct from ?")) {
            update.setDate(1, Date.valueOf(day));
            update.setLong(2, id);
            update.setDate(3, Date.valueOf(day));
            return update.executeUpdate() == 1;
          }
        });
  }

  private void bind(
      Connection connection,
      PreparedStatement upsert,
      GitLabAddress address,
      GitLabToken token,
      TokenInfo info)
      throws SQLException {
    upsert.setString(1, address.toString());
    upsert.setString(2, info.owner());
    upsert.setBytes(3, cipher.encrypt(token));
    upsert.setArray(4, connection.createArrayOf("text", info.scopes().toArray()));
    if (info.expiresAt().isPresent()) {
      upsert.setDate(5, Date.valueOf(info.expiresAt().get()));
    } else {
      upsert.setNull(5, Types.DATE);
    }
  }

  private List<GitLabConnection> read(PreparedStatement select) throws SQLException {
    List<GitLabConnection> connections = new ArrayList<>();
    try (ResultSet rows = select.executeQuery()) {
      while (rows.next()) {
        connections.add(connectionOf(rows));
      }
    }
    return connections;
  }

  private GitLabConnection connectionOf(ResultSet row) throws SQLException {
    String owner = row.getString("username");
    Array scopesArray = row.getArray("scopes");
    Set<String> scopes = Set.of((String[]) scopesArray.getArray());
    Date expiresAt = row.getDate("expires_at");
    TokenInfo info =
        expiresAt == null
            ? TokenInfo.withoutExpiry(owner, scopes)
            : TokenInfo.expiring(owner, scopes, expiresAt.toLocalDate());
    return new GitLabConnection(
        row.getLong("id"),
        GitLabAddress.parse(row.getString("base_url")),
        cipher.decrypt(row.getBytes("token_encrypted")),
        info);
  }
}
