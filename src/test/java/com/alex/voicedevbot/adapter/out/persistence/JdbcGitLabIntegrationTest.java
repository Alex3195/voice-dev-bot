package com.alex.voicedevbot.adapter.out.persistence;

import static com.alex.voicedevbot.support.GitLabFixtures.NEW_TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.REPO;
import static com.alex.voicedevbot.support.GitLabFixtures.TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.VALID;
import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.domain.GitLabAddress;
import com.alex.voicedevbot.domain.GitLabConnection;
import com.alex.voicedevbot.domain.GitLabRepo;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.RepoLink;
import com.alex.voicedevbot.domain.TokenInfo;
import com.alex.voicedevbot.support.GitLabFixtures;
import com.alex.voicedevbot.support.PostgresContainer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** GitLab ulanishlari va project ↔ repo haqiqiy PostgreSQL'da. */
class JdbcGitLabIntegrationTest {

  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");
  private static final GitLabAddress SELF_HOSTED = GitLabAddress.parse("git.example.uz");

  private static DataSource dataSource;

  private final JdbcGitLabConnectionRepository connections =
      new JdbcGitLabConnectionRepository(dataSource, new TokenCipher(TokenCipherTest.KEY));
  private final JdbcProjectRepoLinks links = new JdbcProjectRepoLinks(dataSource);
  private final JdbcProjectRepository projects = new JdbcProjectRepository(dataSource);

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
      statement.execute(
          "truncate project_repo, gitlab_connection, glossary_term, project restart identity"
              + " cascade");
    }
  }

  @Test
  void should_save_and_read_connection_with_encrypted_token() throws Exception {
    GitLabConnection saved = connections.save(SELF_HOSTED, TOKEN, VALID);

    assertThat(connections.find(saved.id())).contains(saved);
    assertThat(connections.findAll()).containsExactly(saved);
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet row = statement.executeQuery("select token_encrypted from gitlab_connection")) {
      row.next();
      assertThat(new String(row.getBytes(1), StandardCharsets.ISO_8859_1))
          .doesNotContain("secretToken");
    }
  }

  @Test
  void should_update_token_of_same_server_and_owner_instead_of_duplicating() {
    GitLabConnection first = connections.save(SELF_HOSTED, TOKEN, VALID);
    TokenInfo withoutExpiry = TokenInfo.withoutExpiry("alex", Set.of("api", "read_user"));

    GitLabConnection second = connections.save(SELF_HOSTED, NEW_TOKEN, withoutExpiry);

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(connections.findAll())
        .containsExactly(new GitLabConnection(first.id(), SELF_HOSTED, NEW_TOKEN, withoutExpiry));
  }

  @Test
  void should_order_connections_and_remove_with_their_repo_links() {
    GitLabConnection selfHosted = connections.save(SELF_HOSTED, TOKEN, VALID);
    GitLabConnection gitLabCom = connections.save(GitLabAddress.GITLAB_COM, TOKEN, VALID);
    projects.save(Project.named(ELT_IMZO));
    links.link(ELT_IMZO, new RepoLink(selfHosted.id(), REPO));

    assertThat(connections.findAll()).containsExactly(selfHosted, gitLabCom);
    connections.remove(selfHosted.id());

    assertThat(connections.find(selfHosted.id())).isEmpty();
    assertThat(links.find(ELT_IMZO)).isEmpty();
  }

  @Test
  void should_claim_expiry_alert_once_per_day_and_reset_after_renewal() {
    GitLabConnection saved = connections.save(SELF_HOSTED, TOKEN, VALID);

    assertThat(connections.claimExpiryAlert(saved.id(), GitLabFixtures.TODAY)).isTrue();
    assertThat(connections.claimExpiryAlert(saved.id(), GitLabFixtures.TODAY)).isFalse();
    assertThat(connections.claimExpiryAlert(saved.id(), GitLabFixtures.TODAY.plusDays(1))).isTrue();
    connections.save(SELF_HOSTED, NEW_TOKEN, VALID);
    assertThat(connections.claimExpiryAlert(saved.id(), GitLabFixtures.TODAY.plusDays(1))).isTrue();
  }

  @Test
  void should_link_replace_and_unlink_project_repo() {
    GitLabConnection saved = connections.save(SELF_HOSTED, TOKEN, VALID);
    projects.save(Project.named(ELT_IMZO));
    GitLabRepo other =
        new GitLabRepo(43, "akfa/elt", URI.create("https://git.example.uz/akfa/elt"));

    links.link(new ProjectName("elt imzo"), new RepoLink(saved.id(), REPO));
    links.link(ELT_IMZO, new RepoLink(saved.id(), other));

    assertThat(links.find(ELT_IMZO)).contains(new RepoLink(saved.id(), other));
    links.unlink(ELT_IMZO);
    assertThat(links.find(ELT_IMZO)).isEmpty();
  }

  @Test
  void should_ignore_link_for_unknown_project() {
    GitLabConnection saved = connections.save(SELF_HOSTED, TOKEN, VALID);

    links.link(new ProjectName("Yo'q"), new RepoLink(saved.id(), REPO));

    assertThat(links.find(new ProjectName("Yo'q"))).isEmpty();
  }
}
