package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.MergeRequest;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.Repo;
import com.alex.voicedevbot.domain.Task;
import com.alex.voicedevbot.domain.TaskStatus;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Faol project tasklari (Issue) bilan amallar natijasi. */
public sealed interface TasksResult
    permits TasksResult.Overview,
        TasksResult.Page,
        TasksResult.Opened,
        TasksResult.Created,
        RepoUnavailable {

  /**
   * @param counts har holatda nechta task; bo'sh holatlar yo'q, {@link TaskStatus} tartibida
   */
  record Overview(ProjectName project, Repo repo, Map<TaskStatus, Integer> counts)
      implements TasksResult {

    public Overview {
      Objects.requireNonNull(project, "project");
      Objects.requireNonNull(repo, "repo");
      Map<TaskStatus, Integer> ordered = new EnumMap<>(TaskStatus.class);
      ordered.putAll(counts);
      counts = Collections.unmodifiableMap(ordered);
    }

    public int total() {
      return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
  }

  /**
   * @param page 0 dan boshlanadi
   */
  record Page(ProjectName project, TaskStatus status, List<Task> tasks, int page, boolean hasNext)
      implements TasksResult {

    public Page {
      Objects.requireNonNull(project, "project");
      Objects.requireNonNull(status, "status");
      tasks = List.copyOf(tasks);
    }
  }

  /**
   * @param status bugungi holat ({@link Task#status})
   */
  record Opened(ProjectName project, Task task, TaskStatus status, List<MergeRequest> mergeRequests)
      implements TasksResult {

    public Opened {
      Objects.requireNonNull(project, "project");
      Objects.requireNonNull(task, "task");
      Objects.requireNonNull(status, "status");
      mergeRequests = List.copyOf(mergeRequests);
    }
  }

  record Created(ProjectName project, Task task) implements TasksResult {}
}
