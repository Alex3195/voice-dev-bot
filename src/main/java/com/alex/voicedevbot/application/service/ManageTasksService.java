package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.ManageTasksUseCase;
import com.alex.voicedevbot.application.port.in.TasksResult;
import com.alex.voicedevbot.domain.NewTask;
import com.alex.voicedevbot.domain.Task;
import com.alex.voicedevbot.domain.TaskStatus;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class ManageTasksService implements ManageTasksUseCase {

  /** Bitta ro'yxatda nechta task. */
  public static final int PAGE_SIZE = 8;

  /** Xizmatdan o'qiladigan issue'lar chegarasi (eng yangilari). */
  public static final int ISSUE_LIMIT = 300;

  private final ProjectRepoAccess access;

  public ManageTasksService(ProjectRepoAccess access) {
    this.access = Objects.requireNonNull(access, "access");
  }

  @Override
  public TasksResult overview(TelegramUserId user) {
    return run(
        user,
        repo -> {
          Map<TaskStatus, Integer> counts = new EnumMap<>(TaskStatus.class);
          for (Task task : issues(repo)) {
            counts.merge(task.status(repo.today()), 1, Integer::sum);
          }
          return new TasksResult.Overview(repo.project(), repo.repo(), counts);
        });
  }

  @Override
  public TasksResult list(TelegramUserId user, TaskStatus status, int page) {
    Objects.requireNonNull(status, "status");
    int current = Math.max(page, 0);
    return run(
        user,
        repo -> {
          List<Task> matching =
              issues(repo).stream()
                  .filter(task -> task.status(repo.today()) == status)
                  .sorted(order(status))
                  .toList();
          int from = Math.min(current * PAGE_SIZE, matching.size());
          int to = Math.min(from + PAGE_SIZE, matching.size());
          return new TasksResult.Page(
              repo.project(), status, matching.subList(from, to), current, to < matching.size());
        });
  }

  @Override
  public TasksResult open(TelegramUserId user, long iid) {
    return run(
        user, repo -> opened(repo, repo.tracker().issue(repo.connection(), repo.repoId(), iid)));
  }

  @Override
  public TasksResult create(TelegramUserId user, NewTask task) {
    Objects.requireNonNull(task, "task");
    return run(
        user,
        repo ->
            new TasksResult.Created(
                repo.project(),
                repo.tracker()
                    .createIssue(repo.connection(), repo.repoId(), task, List.of(AI_TASK_LABEL))));
  }

  @Override
  public TasksResult setOpen(TelegramUserId user, long iid, boolean open) {
    return run(
        user,
        repo ->
            opened(repo, repo.tracker().setIssueOpen(repo.connection(), repo.repoId(), iid, open)));
  }

  private TasksResult opened(ProjectRepoAccess.Ready repo, Task task) {
    return new TasksResult.Opened(
        repo.project(),
        task,
        task.status(repo.today()),
        repo.code().mergeRequests(repo.connection(), repo.repoId(), task.iid()));
  }

  private List<Task> issues(ProjectRepoAccess.Ready repo) {
    return repo.tracker().issues(repo.connection(), repo.repoId(), ISSUE_LIMIT);
  }

  /** Muddati o'tganlar — eng eskisi birinchi; qolganlari xizmat tartibida (eng yangisi). */
  private static Comparator<Task> order(TaskStatus status) {
    return status == TaskStatus.OVERDUE
        ? Comparator.comparing(task -> task.dueDate().orElse(LocalDate.MAX))
        : (left, right) -> 0;
  }

  private TasksResult run(
      TelegramUserId user, Function<ProjectRepoAccess.Ready, TasksResult> action) {
    return access.run(user, action, unavailable -> unavailable);
  }
}
