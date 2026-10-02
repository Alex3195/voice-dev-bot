package com.alex.voicedevbot.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Foydalanuvchining shaxsiy sozlamalari: nutq tili, faol project va Claude modeli (oxirgi ikkisi
 * bo'lmasligi mumkin; model bo'lmasa — standarti).
 */
public final class UserSettings {

  private final TelegramUserId user;
  private final SpeechLanguage language;
  private final ProjectName activeProject;
  private final ModelId model;

  private UserSettings(
      TelegramUserId user, SpeechLanguage language, ProjectName activeProject, ModelId model) {
    this.user = Objects.requireNonNull(user, "user");
    this.language = Objects.requireNonNull(language, "language");
    this.activeProject = activeProject;
    this.model = model;
  }

  public static UserSettings defaults(TelegramUserId user, SpeechLanguage language) {
    return new UserSettings(user, language, null, null);
  }

  public UserSettings withLanguage(SpeechLanguage newLanguage) {
    return new UserSettings(user, newLanguage, activeProject, model);
  }

  public UserSettings withActiveProject(ProjectName project) {
    return new UserSettings(user, language, Objects.requireNonNull(project, "project"), model);
  }

  public UserSettings withModel(ModelId newModel) {
    return new UserSettings(
        user, language, activeProject, Objects.requireNonNull(newModel, "model"));
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

  public Optional<ModelId> model() {
    return Optional.ofNullable(model);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof UserSettings that
        && user.equals(that.user)
        && language.equals(that.language)
        && Objects.equals(activeProject, that.activeProject)
        && Objects.equals(model, that.model);
  }

  @Override
  public int hashCode() {
    return Objects.hash(user, language, activeProject, model);
  }

  @Override
  public String toString() {
    return "UserSettings[user=%s, language=%s, activeProject=%s, model=%s]"
        .formatted(
            user.value(), language.code(), activeProject, model == null ? null : model.value());
  }
}
