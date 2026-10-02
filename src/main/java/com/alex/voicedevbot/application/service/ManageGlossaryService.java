package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.GlossaryCommandResult;
import com.alex.voicedevbot.application.port.in.ManageGlossaryUseCase;
import com.alex.voicedevbot.application.port.out.ProjectRepository;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.Glossary;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class ManageGlossaryService implements ManageGlossaryUseCase {

  private final AccessPolicy accessPolicy;
  private final ProjectRepository projects;
  private final UserSettingsLookup settings;

  public ManageGlossaryService(
      AccessPolicy accessPolicy, ProjectRepository projects, UserSettingsLookup settings) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.projects = Objects.requireNonNull(projects, "projects");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  @Override
  public GlossaryCommandResult showGlossary(TelegramUserId user) {
    return update(user, UnaryOperator.identity());
  }

  @Override
  public GlossaryCommandResult addTerms(TelegramUserId user, List<String> terms) {
    return update(user, glossary -> glossary.add(terms));
  }

  @Override
  public GlossaryCommandResult removeTerms(TelegramUserId user, List<String> terms) {
    return update(user, glossary -> glossary.remove(terms));
  }

  private GlossaryCommandResult update(TelegramUserId user, UnaryOperator<Glossary> change) {
    if (!accessPolicy.isAllowed(user)) {
      return new GlossaryCommandResult.AccessDenied();
    }
    Optional<Project> active = settings.current(user).activeProject().flatMap(projects::find);
    if (active.isEmpty()) {
      return new GlossaryCommandResult.NoActiveProject();
    }
    Project project = active.get();
    Glossary changed = change.apply(project.glossary());
    if (!changed.equals(project.glossary())) {
      projects.save(project.withGlossary(changed));
    }
    return new GlossaryCommandResult.Shown(project.name(), changed.terms());
  }
}
