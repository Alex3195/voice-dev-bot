package com.alex.voicedevbot.application.service;

import static com.alex.voicedevbot.support.GitLabFixtures.REPO;
import static com.alex.voicedevbot.support.GitLabFixtures.TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.application.port.in.GitLabProblem;
import com.alex.voicedevbot.application.port.in.ManageTasksUseCase;
import com.alex.voicedevbot.application.port.in.RepoUnavailable;
import com.alex.voicedevbot.application.port.in.TasksResult;
import com.alex.voicedevbot.application.port.out.GitLabApi;
import com.alex.voicedevbot.application.port.out.GitLabException;
import com.alex.voicedevbot.application.port.out.GitLabException.Reason;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.GitLabAddress;
import com.alex.voicedevbot.domain.GitLabConnection;
import com.alex.voicedevbot.domain.MergeRequest;
import com.alex.voicedevbot.domain.NewTask;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.RepoLink;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.Task;
import com.alex.voicedevbot.domain.TaskStatus;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.UserSettings;
import com.alex.voicedevbot.support.GitLabFixtures;
import com.alex.voicedevbot.support.InMemoryGitLabConnectionRepository;
import com.alex.voicedevbot.support.InMemoryProjectRepoLinks;
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManageTasksServiceTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");
  private static final LocalDate TODAY = GitLabFixtures.TODAY;

  private final InMemoryUserSettingsRepository settingsRepository =
      new InMemoryUserSettingsRepository();
  private final InMemoryGitLabConnectionRepository connections =
      new InMemoryGitLabConnectionRepository();
  private final InMemoryProjectRepoLinks links = new InMemoryProjectRepoLinks();
  private final GitLabApi api = mock(GitLabApi.class);
  private final ManageTasksService service =
      new ManageTasksService(
          new ProjectRepoAccess(
              new AccessPolicy(Set.of(USER)),
              new UserSettingsLookup(settingsRepository, new SpeechLanguage("uz")),
              connections,
              links,
              Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)),
          api);

  private GitLabConnection connection;

  @BeforeEach
  void linkedProject() {
    settingsRepository.save(
        UserSettings.defaults(USER, new SpeechLanguage("uz")).withActiveProject(ELT_IMZO));
    connection = connections.save(GitLabAddress.GITLAB_COM, TOKEN, VALID);
    links.link(ELT_IMZO, new RepoLink(connection.id(), REPO));
  }

  static Task task(long iid, boolean open, LocalDate due, int mergeRequests) {
    return new Task(
        iid,
        "Task " + iid,
        "",
        open,
        Optional.ofNullable(due),
        mergeRequests,
        URI.create("https://gitlab.com/alex/elt-imzo/-/issues/" + iid));
  }

  private void issues(Task... tasks) {
    when(api.issues(connection, REPO.id(), ManageTasksService.ISSUE_LIMIT))
        .thenReturn(List.of(tasks));
  }

  @Test
  void should_count_tasks_per_status_in_status_order() {
    issues(
        task(1, true, null, 0),
        task(2, true, TODAY.minusDays(1), 0),
        task(3, false, null, 1),
        task(4, true, null, 0),
        task(5, false, null, 0));

    TasksResult result = service.overview(USER);

    assertThat(result)
        .isInstanceOfSatisfying(
            TasksResult.Overview.class,
            overview -> {
              assertThat(overview.repo()).isEqualTo(REPO);
              assertThat(overview.total()).isEqualTo(5);
              assertThat(overview.counts())
                  .containsExactly(
                      Map.entry(TaskStatus.OVERDUE, 1),
                      Map.entry(TaskStatus.OPEN, 2),
                      Map.entry(TaskStatus.DONE, 1),
                      Map.entry(TaskStatus.CLOSED, 1));
            });
  }

  @Test
  void should_list_overdue_tasks_oldest_due_date_first() {
    issues(
        task(1, true, TODAY.minusDays(1), 0),
        task(2, true, TODAY.minusDays(9), 0),
        task(3, true, null, 0));

    TasksResult result = service.list(USER, TaskStatus.OVERDUE, 0);

    assertThat(result)
        .isInstanceOfSatisfying(
            TasksResult.Page.class,
            page -> {
              assertThat(page.tasks()).extracting(Task::iid).containsExactly(2L, 1L);
              assertThat(page.hasNext()).isFalse();
            });
  }

  @Test
  void should_page_tasks_of_one_status() {
    issues(
        IntStream.rangeClosed(1, ManageTasksService.PAGE_SIZE + 2)
            .mapToObj(iid -> task(iid, true, null, 0))
            .toArray(Task[]::new));

    TasksResult first = service.list(USER, TaskStatus.OPEN, 0);
    TasksResult second = service.list(USER, TaskStatus.OPEN, 1);

    assertThat(first)
        .isInstanceOfSatisfying(
            TasksResult.Page.class,
            page -> {
              assertThat(page.tasks()).hasSize(ManageTasksService.PAGE_SIZE);
              assertThat(page.hasNext()).isTrue();
            });
    assertThat(second)
        .isInstanceOfSatisfying(
            TasksResult.Page.class,
            page -> {
              assertThat(page.tasks()).extracting(Task::iid).containsExactly(9L, 10L);
              assertThat(page.page()).isEqualTo(1);
              assertThat(page.hasNext()).isFalse();
            });
  }

  @Test
  void should_return_empty_page_beyond_last() {
    issues(task(1, true, null, 0));

    assertThat(service.list(USER, TaskStatus.OPEN, 5))
        .isInstanceOfSatisfying(TasksResult.Page.class, page -> assertThat(page.tasks()).isEmpty());
  }

  @Test
  void should_open_task_with_status_and_merge_requests() {
    Task task = task(7, true, null, 1);
    MergeRequest mr =
        new MergeRequest(
            3, "Login fix", MergeRequest.State.MERGED, URI.create("https://gitlab.com/mr/3"));
    when(api.issue(connection, REPO.id(), 7)).thenReturn(task);
    when(api.mergeRequests(connection, REPO.id(), 7)).thenReturn(List.of(mr));

    assertThat(service.open(USER, 7))
        .isEqualTo(new TasksResult.Opened(ELT_IMZO, task, TaskStatus.IN_REVIEW, List.of(mr)));
  }

  @Test
  void should_create_issue_with_ai_task_label() {
    NewTask draft = new NewTask("Login", "Parolni tiklash");
    Task created = task(8, true, null, 0);
    when(api.createIssue(connection, REPO.id(), draft, List.of(ManageTasksUseCase.AI_TASK_LABEL)))
        .thenReturn(created);

    assertThat(service.create(USER, draft)).isEqualTo(new TasksResult.Created(ELT_IMZO, created));
  }

  @Test
  void should_close_and_reopen_task() {
    Task closed = task(7, false, null, 0);
    Task reopened = task(7, true, null, 0);
    when(api.setIssueOpen(connection, REPO.id(), 7, false)).thenReturn(closed);
    when(api.setIssueOpen(connection, REPO.id(), 7, true)).thenReturn(reopened);

    assertThat(service.setOpen(USER, 7, false))
        .isEqualTo(new TasksResult.Opened(ELT_IMZO, closed, TaskStatus.CLOSED, List.of()));
    assertThat(service.setOpen(USER, 7, true))
        .isEqualTo(new TasksResult.Opened(ELT_IMZO, reopened, TaskStatus.OPEN, List.of()));
  }

  @Test
  void should_ask_to_link_repo_when_project_has_none() {
    links.unlink(ELT_IMZO);

    assertThat(service.overview(USER)).isEqualTo(new RepoUnavailable.NotLinked(ELT_IMZO));
    verifyNoInteractions(api);
  }

  @Test
  void should_require_active_project() {
    settingsRepository.save(UserSettings.defaults(USER, new SpeechLanguage("uz")));

    assertThat(service.overview(USER)).isEqualTo(new RepoUnavailable.NoActiveProject());
  }

  @Test
  void should_ask_for_new_token_without_calling_gitlab_when_token_expired() {
    connections.save(
        GitLabAddress.GITLAB_COM, TOKEN, GitLabFixtures.expiringOn(TODAY.minusDays(1)));

    assertThat(service.overview(USER)).isInstanceOf(RepoUnavailable.NeedsNewToken.class);
    verifyNoInteractions(api);
  }

  @Test
  void should_ask_for_new_token_when_gitlab_rejects_it() {
    when(api.issues(any(), anyLong(), anyInt()))
        .thenThrow(new GitLabException(Reason.UNAUTHORIZED, "GET issues returned 401", null));

    assertThat(service.overview(USER)).isInstanceOf(RepoUnavailable.NeedsNewToken.class);
  }

  @Test
  void should_report_gitlab_failure() {
    when(api.issue(any(), anyLong(), anyLong()))
        .thenThrow(new GitLabException(Reason.NOT_FOUND, "GET issue returned 404", null));

    assertThat(service.open(USER, 99))
        .isEqualTo(new RepoUnavailable.Failed(GitLabProblem.NOT_FOUND));
  }

  @Test
  void should_deny_stranger_without_calling_gitlab() {
    assertThat(service.overview(STRANGER)).isEqualTo(new RepoUnavailable.AccessDenied());
    assertThat(service.create(STRANGER, new NewTask("x", "")))
        .isEqualTo(new RepoUnavailable.AccessDenied());
    verifyNoInteractions(api);
  }

  @Test
  void should_ask_gitlab_for_issues_within_limit() {
    issues();

    service.overview(USER);

    verify(api).issues(connection, REPO.id(), ManageTasksService.ISSUE_LIMIT);
  }
}
