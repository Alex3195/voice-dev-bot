package com.alex.voicedevbot.adapter.out.persistence;

import com.alex.voicedevbot.application.port.out.ProjectRepoLinks;
import com.alex.voicedevbot.domain.GitLabRepo;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.RepoLink;
import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import javax.sql.DataSource;

/** Project ↔ GitLab repo bog'lanishi PostgreSQL'da ({@code project_repo}). */
public class JdbcProjectRepoLinks implements ProjectRepoLinks {

  private static final String UPSERT =
      """
      insert into project_repo (project_id, connection_id, gitlab_project_id, path, web_url)
      select id, ?, ?, ?, ? from project where lower(name) = ?
      on conflict (project_id) do update
      set connection_id = excluded.connection_id, gitlab_project_id = excluded.gitlab_project_id,
          path = excluded.path, web_url = excluded.web_url
      """;
  private static final String SELECT =
      """
      select r.connection_id, r.gitlab_project_id, r.path, r.web_url
      from project_repo r join project p on p.id = r.project_id
      where lower(p.name) = ?
      """;

  private final Jdbc jdbc;

  public JdbcProjectRepoLinks(DataSource dataSource) {
    this.jdbc = new Jdbc(dataSource);
  }

  @Override
  public void link(ProjectName project, RepoLink link) {
    jdbc.query(
        "link project repository",
        connection -> {
          try (PreparedStatement upsert = connection.prepareStatement(UPSERT)) {
            upsert.setLong(1, link.connectionId());
            upsert.setLong(2, link.repo().id());
            upsert.setString(3, link.repo().path());
            upsert.setString(4, link.repo().webUrl().toString());
            upsert.setString(5, project.key());
            return upsert.executeUpdate();
          }
        });
  }

  @Override
  public Optional<RepoLink> find(ProjectName project) {
    return jdbc.query(
        "find project repository",
        connection -> {
          try (PreparedStatement select = connection.prepareStatement(SELECT)) {
            select.setString(1, project.key());
            try (ResultSet rows = select.executeQuery()) {
              if (!rows.next()) {
                return Optional.empty();
              }
              GitLabRepo repo =
                  new GitLabRepo(
                      rows.getLong("gitlab_project_id"),
                      rows.getString("path"),
                      URI.create(rows.getString("web_url")));
              return Optional.of(new RepoLink(rows.getLong("connection_id"), repo));
            }
          }
        });
  }

  @Override
  public void unlink(ProjectName project) {
    jdbc.query(
        "unlink project repository",
        connection -> {
          try (PreparedStatement delete =
              connection.prepareStatement(
                  "delete from project_repo where project_id ="
                      + " (select id from project where lower(name) = ?)")) {
            delete.setString(1, project.key());
            return delete.executeUpdate();
          }
        });
  }
}
