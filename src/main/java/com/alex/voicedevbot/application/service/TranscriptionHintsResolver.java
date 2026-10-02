package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.out.ProjectRepository;
import com.alex.voicedevbot.domain.Glossary;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.TranscriptionHints;
import com.alex.voicedevbot.domain.UserSettings;
import java.util.List;
import java.util.Objects;

/** Foydalanuvchi tili va faol project lug'atidan STT ishoralarini yig'adi. */
public class TranscriptionHintsResolver {

  private final UserSettingsLookup settings;
  private final ProjectRepository projects;

  public TranscriptionHintsResolver(UserSettingsLookup settings, ProjectRepository projects) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.projects = Objects.requireNonNull(projects, "projects");
  }

  public TranscriptionHints resolve(TelegramUserId user) {
    UserSettings current = settings.current(user);
    List<String> vocabulary =
        current
            .activeProject()
            .flatMap(projects::find)
            .map(Project::glossary)
            .map(Glossary::terms)
            .orElse(List.of());
    return new TranscriptionHints(current.language(), vocabulary);
  }
}
