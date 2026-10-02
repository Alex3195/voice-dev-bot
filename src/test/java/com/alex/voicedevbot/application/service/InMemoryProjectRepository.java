package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.out.ProjectRepository;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link ProjectRepository} shartnomasini xotirada bajaradi: nom katta-kichik harfga qaramaydi. */
class InMemoryProjectRepository implements ProjectRepository {

  private final Map<String, Project> projects = new HashMap<>();

  @Override
  public Optional<Project> find(ProjectName name) {
    return Optional.ofNullable(projects.get(name.key()));
  }

  @Override
  public List<Project> findAll() {
    return projects.values().stream()
        .sorted(Comparator.comparing(project -> project.name().key()))
        .toList();
  }

  @Override
  public void save(Project project) {
    Project stored =
        find(project.name())
            .map(existing -> existing.withGlossary(project.glossary()))
            .orElse(project);
    projects.put(project.name().key(), stored);
  }
}
