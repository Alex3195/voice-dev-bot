package com.alex.voicedevbot.support;

import com.alex.voicedevbot.application.port.out.ProjectRepoLinks;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.RepoLink;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryProjectRepoLinks implements ProjectRepoLinks {

  private final Map<String, RepoLink> links = new HashMap<>();

  @Override
  public void link(ProjectName project, RepoLink link) {
    links.put(project.key(), link);
  }

  @Override
  public Optional<RepoLink> find(ProjectName project) {
    return Optional.ofNullable(links.get(project.key()));
  }

  @Override
  public void unlink(ProjectName project) {
    links.remove(project.key());
  }
}
