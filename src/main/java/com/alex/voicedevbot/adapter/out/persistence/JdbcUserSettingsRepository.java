package com.alex.voicedevbot.adapter.out.persistence;

import com.alex.voicedevbot.application.port.out.UserSettingsRepository;
import com.alex.voicedevbot.domain.ModelId;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.UserSettings;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

/** Foydalanuvchi sozlamalari PostgreSQL'da; faol project nomi orqali bog'lanadi. */
public class JdbcUserSettingsRepository implements UserSettingsRepository {

  private static final String SELECT =
      """
      select s.language, s.model, p.name as active_project
      from user_settings s
      left join project p on p.id = s.active_project_id
      where s.telegram_user_id = ?
      """;
  private static final String UPSERT =
      """
      insert into user_settings (telegram_user_id, language, active_project_id, model)
      values (?, ?, (select id from project where lower(name) = ?), ?)
      on conflict (telegram_user_id) do update
      set language = excluded.language, active_project_id = excluded.active_project_id,
          model = excluded.model
      """;

  private final Jdbc jdbc;

  public JdbcUserSettingsRepository(DataSource dataSource) {
    this.jdbc = new Jdbc(dataSource);
  }

  @Override
  public Optional<UserSettings> find(TelegramUserId user) {
    return jdbc.query(
        "find user settings",
        connection -> {
          try (PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setLong(1, user.value());
            try (ResultSet rows = statement.executeQuery()) {
              return rows.next() ? Optional.of(toSettings(user, rows)) : Optional.empty();
            }
          }
        });
  }

  @Override
  public void save(UserSettings settings) {
    jdbc.query(
        "save user settings",
        connection -> {
          try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setLong(1, settings.user().value());
            statement.setString(2, settings.language().code());
            statement.setString(3, settings.activeProject().map(ProjectName::key).orElse(null));
            statement.setString(4, settings.model().map(ModelId::value).orElse(null));
            return statement.executeUpdate();
          }
        });
  }

  private static UserSettings toSettings(TelegramUserId user, ResultSet rows) throws SQLException {
    UserSettings settings =
        UserSettings.defaults(user, new SpeechLanguage(rows.getString("language")));
    String activeProject = rows.getString("active_project");
    if (activeProject != null) {
      settings = settings.withActiveProject(new ProjectName(activeProject));
    }
    String model = rows.getString("model");
    return model == null ? settings : settings.withModel(new ModelId(model));
  }
}
