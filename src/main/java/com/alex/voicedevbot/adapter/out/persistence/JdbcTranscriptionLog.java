package com.alex.voicedevbot.adapter.out.persistence;

import com.alex.voicedevbot.application.port.out.TranscriptionLog;
import com.alex.voicedevbot.domain.AudioKind;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.LoggedTranscript;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SourceAudio;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.Transcript;
import com.alex.voicedevbot.domain.TranscriptFilter;
import com.alex.voicedevbot.domain.TranscriptRecord;
import com.alex.voicedevbot.domain.Transcription;
import com.alex.voicedevbot.domain.UserSettings;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/** Transkripsiya jurnali PostgreSQL'da ({@code transcription} jadvali). */
public class JdbcTranscriptionLog implements TranscriptionLog {

  private static final String INSERT =
      """
      insert into transcription (telegram_user_id, project_id, language, prompt, model, raw_text,
          audio_path, telegram_file_id, audio_kind, mime_type, duration_seconds, created_at)
      values (?, (select id from project where lower(name) = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      returning id
      """;
  private static final String SELECT =
      """
      select t.*, p.name as project_name
      from transcription t
      left join project p on p.id = t.project_id
      """;
  private static final String NEWEST_FIRST = " order by t.created_at desc, t.id desc";

  private final Jdbc jdbc;

  public JdbcTranscriptionLog(DataSource dataSource) {
    this.jdbc = new Jdbc(dataSource);
  }

  @Override
  public long append(TranscriptRecord record) {
    return jdbc.query(
        "log transcript",
        connection -> {
          try (PreparedStatement insert = connection.prepareStatement(INSERT)) {
            bind(insert, record);
            try (ResultSet rows = insert.executeQuery()) {
              rows.next();
              return rows.getLong("id");
            }
          }
        });
  }

  @Override
  public List<LoggedTranscript> list(TranscriptFilter filter, int offset, int limit) {
    String where =
        switch (filter) {
          case TranscriptFilter.OfProject ignored -> " where lower(p.name) = ?";
          case TranscriptFilter.WithoutProject() -> " where t.project_id is null";
        };
    return jdbc.query(
        "list transcripts",
        connection -> {
          try (PreparedStatement select =
              connection.prepareStatement(SELECT + where + NEWEST_FIRST + " offset ? limit ?")) {
            int index = 1;
            if (filter instanceof TranscriptFilter.OfProject(var project)) {
              select.setString(index++, project.key());
            }
            select.setInt(index++, offset);
            select.setInt(index, limit);
            return read(select);
          }
        });
  }

  @Override
  public Optional<LoggedTranscript> find(long id) {
    return jdbc.query(
        "find transcript",
        connection -> {
          try (PreparedStatement select = connection.prepareStatement(SELECT + " where t.id = ?")) {
            select.setLong(1, id);
            return read(select).stream().findFirst();
          }
        });
  }

  private static void bind(PreparedStatement insert, TranscriptRecord record) throws SQLException {
    Transcription transcription = record.transcription();
    SourceAudio audio = record.audio();
    insert.setLong(1, record.user().value());
    insert.setString(2, record.project().map(ProjectName::key).orElse(null));
    insert.setString(3, record.language().code());
    insert.setString(4, transcription.prompt());
    insert.setString(5, transcription.model());
    insert.setString(6, transcription.transcript().text());
    if (record.archivePath().isPresent()) {
      insert.setString(7, record.archivePath().get());
    } else {
      insert.setNull(7, Types.VARCHAR);
    }
    insert.setString(8, audio.ref().id());
    insert.setString(9, audio.kind().name());
    insert.setString(10, audio.ref().mimeType());
    insert.setInt(11, Math.toIntExact(audio.duration().toSeconds()));
    insert.setTimestamp(12, Timestamp.from(record.createdAt()));
  }

  private static List<LoggedTranscript> read(PreparedStatement select) throws SQLException {
    List<LoggedTranscript> transcripts = new ArrayList<>();
    try (ResultSet rows = select.executeQuery()) {
      while (rows.next()) {
        transcripts.add(new LoggedTranscript(rows.getLong("id"), recordOf(rows)));
      }
    }
    return transcripts;
  }

  private static TranscriptRecord recordOf(ResultSet row) throws SQLException {
    UserSettings speaker =
        UserSettings.defaults(
            new TelegramUserId(row.getLong("telegram_user_id")),
            new SpeechLanguage(row.getString("language")));
    String project = row.getString("project_name");
    if (project != null) {
      speaker = speaker.withActiveProject(new ProjectName(project));
    }
    TranscriptRecord record =
        TranscriptRecord.of(
            speaker,
            new Transcription(
                new Transcript(row.getString("raw_text")),
                row.getString("prompt"),
                row.getString("model")),
            new SourceAudio(
                new AudioRef(row.getString("telegram_file_id"), row.getString("mime_type")),
                AudioKind.valueOf(row.getString("audio_kind")),
                Duration.ofSeconds(row.getInt("duration_seconds"))),
            row.getTimestamp("created_at").toInstant());
    String archivePath = row.getString("audio_path");
    return archivePath == null ? record : record.withArchivePath(archivePath);
  }
}
