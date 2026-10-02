package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.RepoLink;
import java.util.Optional;

/** Project ↔ GitLab repo bog'lanishi; har projectda ko'pi bilan bitta repo. */
public interface ProjectRepoLinks {

  /** Avvalgi bog'lanish almashtiriladi. */
  void link(ProjectName project, RepoLink link);

  Optional<RepoLink> find(ProjectName project);

  void unlink(ProjectName project);
}
