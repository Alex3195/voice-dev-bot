package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.ManageProjectsUseCase;
import com.alex.voicedevbot.application.port.in.ProjectCommandResult;
import com.alex.voicedevbot.application.port.in.ProjectCommandResult.ProjectSummary;
import com.alex.voicedevbot.application.port.out.ProjectRepository;
import com.alex.voicedevbot.application.port.out.UserSettingsRepository;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.UserSettings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ManageProjectsService implements ManageProjectsUseCase {

  private final AccessPolicy accessPolicy;
  private final ProjectRepository projects;
  private final UserSettingsLookup settings;
  private final UserSettingsRepository settingsRepository;

  public ManageProjectsService(
      AccessPolicy accessPolicy,
      ProjectRepository projects,
      UserSettingsLookup settings,
      UserSettingsRepository settingsRepository) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.projects = Objects.requireNonNull(projects, "projects");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository");
  }

  @Override
  public ProjectCommandResult addProject(TelegramUserId user, ProjectName name) {
    if (!accessPolicy.isAllowed(user)) {
      return new ProjectCommandResult.AccessDenied();
    }
    Optional<Project> existing = projects.find(name);
    if (existing.isPresent()) {
      return new ProjectCommandResult.AlreadyExists(existing.get().name());
    }
    projects.save(Project.named(name));
    activate(user, name);
    return new ProjectCommandResult.Added(name);
  }

  @Override
  public ProjectCommandResult selectProject(TelegramUserId user, ProjectName name) {
    if (!accessPolicy.isAllowed(user)) {
      return new ProjectCommandResult.AccessDenied();
    }
    return projects
        .find(name)
        .map(Project::name)
        .<ProjectCommandResult>map(
            found -> {
              activate(user, found);
              return new ProjectCommandResult.Selected(found);
            })
        .orElseGet(() -> new ProjectCommandResult.NotFound(name));
  }

  @Override
  public ProjectCommandResult listProjects(TelegramUserId user) {
    if (!accessPolicy.isAllowed(user)) {
      return new ProjectCommandResult.AccessDenied();
    }
    Optional<ProjectName> active = settings.current(user).activeProject();
    List<ProjectSummary> summaries =
        projects.findAll().stream()
            .map(project -> summaryOf(project, active.map(project.name()::sameAs).orElse(false)))
            .toList();
    return new ProjectCommandResult.Listed(summaries);
  }

  private void activate(TelegramUserId user, ProjectName name) {
    UserSettings current = settings.current(user);
    settingsRepository.save(current.withActiveProject(name));
  }

  private static ProjectSummary summaryOf(Project project, boolean active) {
    return new ProjectSummary(project.name(), project.glossary().terms().size(), active);
  }
}
