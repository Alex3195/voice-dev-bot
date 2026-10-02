package com.alex.voicedevbot.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.domain.Glossary;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.UserSettings;
import com.alex.voicedevbot.support.PostgresContainer;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Repository'lar haqiqiy PostgreSQL'da, Flyway migratsiyalari bilan. */
class JdbcRepositoriesIntegrationTest {

  private static final TelegramUserId USER = new TelegramUserId(42L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");
  private static final SpeechLanguage UZ = new SpeechLanguage("uz");

  private static DataSource dataSource;

  private final JdbcProjectRepository projects = new JdbcProjectRepository(dataSource);
  private final JdbcUserSettingsRepository settings = new JdbcUserSettingsRepository(dataSource);

  @BeforeAll
  static void migrate() {
    PostgreSQLContainer postgres = PostgresContainer.instance();
    dataSource =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    Flyway.configure().dataSource(dataSource).load().migrate();
  }

  @BeforeEach
  void cleanTables() throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("truncate user_settings, glossary_term, project restart identity cascade");
    }
  }

  @Test
  void should_save_and_find_project_with_glossary_in_order() {
    Project project = new Project(ELT_IMZO, Glossary.of(List.of("kassa bo'limi", "Klaes", "PVX")));

    projects.save(project);

    assertThat(projects.find(new ProjectName("elt IMZO"))).contains(project);
  }

  @Test
  void should_find_project_without_terms() {
    projects.save(Project.named(ELT_IMZO));

    assertThat(projects.find(ELT_IMZO)).contains(Project.named(ELT_IMZO));
    assertThat(projects.find(new ProjectName("Yo'q"))).isEmpty();
  }

  @Test
  void should_replace_glossary_and_keep_original_name_when_saved_again() {
    projects.save(new Project(ELT_IMZO, Glossary.of(List.of("kassa", "Klaes"))));

    projects.save(new Project(new ProjectName("elt imzo"), Glossary.of(List.of("PVX"))));

    assertThat(projects.findAll())
        .containsExactly(new Project(ELT_IMZO, Glossary.of(List.of("PVX"))));
  }

  @Test
  void should_list_projects_alphabetically_ignoring_case() {
    projects.save(Project.named(new ProjectName("finbank")));
    projects.save(Project.named(ELT_IMZO));
    projects.save(Project.named(new ProjectName("Akfa")));

    assertThat(projects.findAll())
        .extracting(project -> project.name().value())
        .containsExactly("Akfa", "ELT imzo", "finbank");
  }

  @Test
  void should_save_and_update_user_settings_with_active_project() {
    projects.save(Project.named(ELT_IMZO));
    UserSettings initial = UserSettings.defaults(USER, UZ);

    settings.save(initial);
    UserSettings changed =
        initial
            .withActiveProject(new ProjectName("elt imzo"))
            .withLanguage(new SpeechLanguage("kk"));
    settings.save(changed);

    assertThat(settings.find(USER))
        .contains(
            UserSettings.defaults(USER, new SpeechLanguage("kk")).withActiveProject(ELT_IMZO));
    assertThat(settings.find(new TelegramUserId(7L))).isEmpty();
  }

  @Test
  void should_keep_settings_without_project_when_user_has_none() {
    settings.save(UserSettings.defaults(USER, UZ));

    assertThat(settings.find(USER)).contains(UserSettings.defaults(USER, UZ));
  }

  @Test
  void should_wrap_database_errors_in_storage_exception() {
    DataSource broken = new DriverManagerDataSource("jdbc:postgresql://localhost:1/none", "x", "y");

    assertThatThrownBy(() -> new JdbcProjectRepository(broken).findAll())
        .isInstanceOf(StorageException.class)
        .hasMessage("Failed to list projects");
    assertThatThrownBy(() -> new JdbcUserSettingsRepository(broken).find(USER))
        .isInstanceOf(StorageException.class);
  }

  @Test
  void should_roll_back_whole_transaction_when_a_later_statement_fails() {
    Jdbc jdbc = new Jdbc(dataSource);

    assertThatThrownBy(
            () ->
                jdbc.inTransaction(
                    "save project",
                    connection -> {
                      try (Statement statement = connection.createStatement()) {
                        statement.execute("insert into project (name) values ('Yangi')");
                      }
                      throw new SQLException("boom");
                    }))
        .isInstanceOf(StorageException.class)
        .hasRootCauseMessage("boom");

    assertThat(projects.find(new ProjectName("Yangi"))).isEmpty();
  }
}
