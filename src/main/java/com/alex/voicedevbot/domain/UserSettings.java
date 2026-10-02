package com.alex.voicedevbot.domain;

import java.util.Objects;
import java.util.Optional;

/** Foydalanuvchining shaxsiy sozlamalari: nutq tili va faol project (bo'lmasligi mumkin). */
public final class UserSettings {

  private final TelegramUserId user;
  private final SpeechLanguage language;
  private final ProjectName activeProject;

  private UserSettings(TelegramUserId user, SpeechLanguage language, ProjectName activeProject) {
    this.user = Objects.requireNonNull(user, "user");
    this.language = Objects.requireNonNull(language, "language");
    this.activeProject = activeProject;
  }

  public static UserSettings defaults(TelegramUserId user, SpeechLanguage language) {
    return new UserSettings(user, language, null);
  }

  public UserSettings withLanguage(SpeechLanguage newLanguage) {
    return new UserSettings(user, newLanguage, activeProject);
  }

  public UserSettings withActiveProject(ProjectName project) {
    return new UserSettings(user, language, Objects.requireNonNull(project, "project"));
  }

  public TelegramUserId user() {
    return user;
  }

  public SpeechLanguage language() {
    return language;
  }

  public Optional<ProjectName> activeProject() {
    return Optional.ofNullable(activeProject);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof UserSettings that
        && user.equals(that.user)
        && language.equals(that.language)
        && Objects.equals(activeProject, that.activeProject);
  }

  @Override
  public int hashCode() {
    return Objects.hash(user, language, activeProject);
  }

  @Override
  public String toString() {
    return "UserSettings[user=%s, language=%s, activeProject=%s]"
        .formatted(user.value(), language.code(), activeProject);
  }
}
