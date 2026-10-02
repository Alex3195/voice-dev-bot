package com.alex.voicedevbot.adapter.out.persistence;

import static com.alex.voicedevbot.support.GitLabFixtures.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.Provider;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.Repo;
import com.alex.voicedevbot.domain.RepoLink;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.TokenInfo;
import com.alex.voicedevbot.support.PostgresContainer;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V4: PR C'da saqlangan GitLab ulanishlari va repo havolalari umumiy ulanishlarga o'tganda
 * yo'qolmaydi — alohida sxemada V3 gacha ko'tarib, ma'lumot yozib, keyin oxirigacha migratsiya.
 */
class ConnectionsMigrationIntegrationTest {

  private static final String SCHEMA = "v3_upgrade";

  @Test
  void should_keep_existing_gitlab_connections_and_repo_links_after_upgrade() throws Exception {
    DataSource dataSource = freshSchema();
    Flyway.configure().dataSource(dataSource).schemas(SCHEMA).target("3").load().migrate();
    TokenCipher cipher = new TokenCipher(TokenCipherTest.KEY);
    try (Connection connection = dataSource.getConnection();
        PreparedStatement insert =
            connection.prepareStatement(
                "insert into gitlab_connection (base_url, username, token_encrypted, scopes,"
                    + " expires_at) values ('https://git.example.uz', 'alex', ?, '{api}',"
                    + " date '2027-01-01')");
        Statement statement = connection.createStatement()) {
      insert.setBytes(1, cipher.encrypt(TOKEN));
      insert.executeUpdate();
      statement.execute("insert into project (name) values ('ELT imzo')");
      statement.execute(
          "insert into project_repo (project_id, connection_id, gitlab_project_id, path, web_url)"
              + " values (1, 1, 42, 'alex/elt-imzo', 'https://git.example.uz/alex/elt-imzo')");
    }

    Flyway.configure().dataSource(dataSource).schemas(SCHEMA).load().migrate();

    JdbcConnectionRepository connections = new JdbcConnectionRepository(dataSource, cipher);
    assertThat(connections.findAll())
        .containsExactly(
            new ProviderConnection(
                1,
                Provider.GITLAB,
                ServerAddress.parse("git.example.uz"),
                TOKEN,
                TokenInfo.expiring("alex", Set.of("api"), LocalDate.of(2027, 1, 1))));
    assertThat(new JdbcProjectRepoLinks(dataSource).find(new ProjectName("ELT imzo")))
        .contains(
            new RepoLink(
                1,
                new Repo(42, "alex/elt-imzo", URI.create("https://git.example.uz/alex/elt-imzo"))));
  }

  private static DataSource freshSchema() throws Exception {
    PostgreSQLContainer postgres = PostgresContainer.instance();
    DataSource admin =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    try (Connection connection = admin.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("drop schema if exists " + SCHEMA + " cascade");
    }
    String url = postgres.getJdbcUrl();
    String separator = url.contains("?") ? "&" : "?";
    return new DriverManagerDataSource(
        url + separator + "currentSchema=" + SCHEMA,
        postgres.getUsername(),
        postgres.getPassword());
  }
}
