package com.alex.voicedevbot.adapter.out.persistence;

import com.alex.voicedevbot.application.port.out.ProjectRepository;
import com.alex.voicedevbot.domain.Glossary;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

/** Projectlar va lug'atlar PostgreSQL'da (sxema: {@code db/migration}). */
public class JdbcProjectRepository implements ProjectRepository {

  private static final String SELECT_WITH_TERMS =
      """
      select p.name, g.term
      from project p
      left join glossary_term g on g.project_id = p.id
      """;
  private static final String ORDER = " order by lower(p.name), g.position";

  private final Jdbc jdbc;

  public JdbcProjectRepository(DataSource dataSource) {
    this.jdbc = new Jdbc(dataSource);
  }

  @Override
  public Optional<Project> find(ProjectName name) {
    return jdbc.query(
        "find project",
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(SELECT_WITH_TERMS + " where lower(p.name) = ?" + ORDER)) {
            statement.setString(1, name.key());
            return readProjects(statement).stream().findFirst();
          }
        });
  }

  @Override
  public List<Project> findAll() {
    return jdbc.query(
        "list projects",
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(SELECT_WITH_TERMS + ORDER)) {
            return readProjects(statement);
          }
        });
  }

  @Override
  public void save(Project project) {
    jdbc.inTransaction(
        "save project",
        connection -> {
          long id = upsertProject(connection, project.name());
          replaceTerms(connection, id, project.glossary());
          return null;
        });
  }

  private static long upsertProject(Connection connection, ProjectName name) throws SQLException {
    try (PreparedStatement insert =
        connection.prepareStatement(
            "insert into project (name) values (?) on conflict (lower(name)) do nothing")) {
      insert.setString(1, name.value());
      insert.executeUpdate();
    }
    try (PreparedStatement select =
        connection.prepareStatement("select id from project where lower(name) = ?")) {
      select.setString(1, name.key());
      try (ResultSet rows = select.executeQuery()) {
        rows.next();
        return rows.getLong("id");
      }
    }
  }

  private static void replaceTerms(Connection connection, long projectId, Glossary glossary)
      throws SQLException {
    try (PreparedStatement delete =
        connection.prepareStatement("delete from glossary_term where project_id = ?")) {
      delete.setLong(1, projectId);
      delete.executeUpdate();
    }
    try (PreparedStatement insert =
        connection.prepareStatement(
            "insert into glossary_term (project_id, position, term) values (?, ?, ?)")) {
      List<String> terms = glossary.terms();
      for (int position = 0; position < terms.size(); position++) {
        insert.setLong(1, projectId);
        insert.setInt(2, position);
        insert.setString(3, terms.get(position));
        insert.addBatch();
      }
      insert.executeBatch();
    }
  }

  /** Har bir project uchun bitta qator (atamasiz) yoki har atama uchun bitta qator keladi. */
  private static List<Project> readProjects(PreparedStatement statement) throws SQLException {
    Map<String, List<String>> termsByProject = new LinkedHashMap<>();
    try (ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        List<String> terms =
            termsByProject.computeIfAbsent(rows.getString("name"), name -> new ArrayList<>());
        String term = rows.getString("term");
        if (term != null) {
          terms.add(term);
        }
      }
    }
    return termsByProject.entrySet().stream()
        .map(entry -> new Project(new ProjectName(entry.getKey()), Glossary.of(entry.getValue())))
        .toList();
  }
}
