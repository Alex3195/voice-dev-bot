package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.ProjectName;
import java.util.List;
import java.util.Objects;

/** Project buyruqlari natijasi. */
public sealed interface ProjectCommandResult {

  /** Project yaratildi va faol qilindi. */
  record Added(ProjectName name) implements ProjectCommandResult {}

  record AlreadyExists(ProjectName name) implements ProjectCommandResult {}

  record Selected(ProjectName name) implements ProjectCommandResult {}

  record NotFound(ProjectName name) implements ProjectCommandResult {}

  record Listed(List<ProjectSummary> projects) implements ProjectCommandResult {

    public Listed {
      projects = List.copyOf(projects);
    }
  }

  /** Yuboruvchi whitelist'da yo'q. */
  record AccessDenied() implements ProjectCommandResult {}

  /**
   * @param termCount lug'atdagi atamalar soni
   * @param active foydalanuvchining faol projectimi
   */
  record ProjectSummary(ProjectName name, int termCount, boolean active) {

    public ProjectSummary {
      Objects.requireNonNull(name, "name");
    }
  }
}
