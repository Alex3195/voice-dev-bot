package com.alex.voicedevbot.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.domain.AudioKind;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.LoggedTranscript;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SourceAudio;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.Transcript;
import com.alex.voicedevbot.domain.TranscriptFilter;
import com.alex.voicedevbot.domain.TranscriptRecord;
import com.alex.voicedevbot.domain.Transcription;
import com.alex.voicedevbot.domain.UserSettings;
import com.alex.voicedevbot.support.PostgresContainer;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Transkripsiya jurnali haqiqiy PostgreSQL'da, Flyway migratsiyalari bilan. */
class JdbcTranscriptionLogIntegrationTest {

  private static final TelegramUserId USER = new TelegramUserId(42L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");
  private static final Instant AT = Instant.parse("2026-10-02T09:30:00Z");

  private static DataSource dataSource;

  private final JdbcTranscriptionLog log = new JdbcTranscriptionLog(dataSource);
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
      statement.execute("truncate transcription, glossary_term, project restart identity cascade");
    }
  }

  private static TranscriptRecord record(ProjectName project, String text, Instant at) {
    UserSettings speaker = UserSettings.defaults(USER, new SpeechLanguage("kk"));
    return TranscriptRecord.of(
        project == null ? speaker : speaker.withActiveProject(project),
        new Transcription(new Transcript(text), "Lotin yozuvida. PVX.", "whisper.cpp large-v3"),
        new SourceAudio(
            new AudioRef("file-id", "video/mp4"), AudioKind.VIDEO_NOTE, Duration.ofSeconds(42)),
        at);
  }

  @Test
  void should_append_and_find_transcript_with_all_fields() {
    projects.save(Project.named(ELT_IMZO));
    TranscriptRecord record =
        record(new ProjectName("elt imzo"), "kassa", AT).withArchivePath("2026-10-02/a.mp4");

    long id = log.append(record);

    assertThat(log.find(id))
        .contains(
            new LoggedTranscript(
                id, record(ELT_IMZO, "kassa", AT).withArchivePath("2026-10-02/a.mp4")));
    assertThat(log.find(id + 1)).isEmpty();
  }

  @Test
  void should_keep_transcript_without_archive_path_and_project() {
    long id = log.append(record(null, "matn", AT));

    assertThat(log.find(id)).contains(new LoggedTranscript(id, record(null, "matn", AT)));
  }

  @Test
  void should_list_by_project_newest_first_with_offset_and_limit() {
    projects.save(Project.named(ELT_IMZO));
    log.append(record(ELT_IMZO, "birinchi", AT));
    log.append(record(ELT_IMZO, "ikkinchi", AT.plusSeconds(60)));
    log.append(record(ELT_IMZO, "uchinchi", AT.plusSeconds(120)));
    log.append(record(null, "projectsiz", AT.plusSeconds(180)));

    assertThat(log.list(new TranscriptFilter.OfProject(new ProjectName("ELT IMZO")), 1, 5))
        .extracting(entry -> entry.record().transcription().transcript().text())
        .containsExactly("ikkinchi", "birinchi");
    assertThat(log.list(new TranscriptFilter.WithoutProject(), 0, 5))
        .extracting(LoggedTranscript::id)
        .containsExactly(4L);
  }

  @Test
  void should_log_as_without_project_when_project_is_unknown() {
    long id = log.append(record(new ProjectName("Yo'q"), "matn", AT));

    assertThat(log.find(id).orElseThrow().record().project()).isEmpty();
  }

  @Test
  void should_wrap_database_errors_in_storage_exception() {
    DataSource broken = new DriverManagerDataSource("jdbc:postgresql://localhost:1/none", "x", "y");
    JdbcTranscriptionLog brokenLog = new JdbcTranscriptionLog(broken);

    assertThatThrownBy(() -> brokenLog.append(record(null, "matn", AT)))
        .isInstanceOf(StorageException.class)
        .hasMessage("Failed to log transcript");
    assertThatThrownBy(() -> brokenLog.find(1)).isInstanceOf(StorageException.class);
  }
}
