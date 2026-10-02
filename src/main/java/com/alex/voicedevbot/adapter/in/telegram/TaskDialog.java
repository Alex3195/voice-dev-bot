package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.adapter.in.telegram.BotConversation.Reply;
import com.alex.voicedevbot.application.port.in.BrowseTranscriptsUseCase;
import com.alex.voicedevbot.application.port.in.LinkRepoUseCase;
import com.alex.voicedevbot.application.port.in.ManageTasksUseCase;
import com.alex.voicedevbot.application.port.in.RepoLinkResult;
import com.alex.voicedevbot.application.port.in.RepoUnavailable;
import com.alex.voicedevbot.application.port.in.TasksResult;
import com.alex.voicedevbot.application.port.in.TranscriptsResult;
import com.alex.voicedevbot.domain.NewTask;
import com.alex.voicedevbot.domain.TaskStatus;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faol project tasklari ({@code ✅ Tasklar}): holat bo'yicha guruhlar, task ichida yopish/qayta
 * ochish, yangi task (qo'lda yoki transkriptdan). Tugmalari {@link Actions#TASKS} bilan boshlanadi.
 *
 * <p>Yangi task qoralamasi va kutilayotgan kiritish xotirada: bot qayta ishga tushsa yo'qoladi.
 * Tasdiqsiz ({@code ✅ Yaratish}) hech narsa yaratilmaydi.
 */
public class TaskDialog {

  /** Transkriptdan olinadigan sarlavha uzunligi. */
  static final int TITLE_FROM_TEXT = 80;

  static final String INVALID_TITLE =
      "⚠️ Sarlavha bo'sh yoki juda uzun (" + NewTask.MAX_TITLE_LENGTH + " belgigacha).";

  private static final Pattern ID = Pattern.compile("\\d{1,18}");
  private static final Pattern GROUP = Pattern.compile("([A-Z_]{1,20}):(\\d{1,6})");

  /** Bot foydalanuvchidan nimani kutyapti. */
  private enum Awaiting {
    TITLE,
    DESCRIPTION,
    EDITED_TITLE,
    EDITED_DESCRIPTION
  }

  /** Sarlavha kiritilgach, tavsif hali bo'lmasligi mumkin. */
  private record Draft(String title, Optional<String> description) {

    NewTask task() {
      return new NewTask(title, description.orElse(""));
    }
  }

  private final ManageTasksUseCase tasks;
  private final LinkRepoUseCase repos;
  private final BrowseTranscriptsUseCase transcripts;
  private final Map<TelegramUserId, Awaiting> awaiting = new ConcurrentHashMap<>();
  private final Map<TelegramUserId, Draft> drafts = new ConcurrentHashMap<>();

  public TaskDialog(
      ManageTasksUseCase tasks, LinkRepoUseCase repos, BrowseTranscriptsUseCase transcripts) {
    this.tasks = Objects.requireNonNull(tasks, "tasks");
    this.repos = Objects.requireNonNull(repos, "repos");
    this.transcripts = Objects.requireNonNull(transcripts, "transcripts");
  }

  static boolean handles(String action) {
    return action.equals(Actions.TASKS) || action.startsWith(Actions.TASK_PREFIX);
  }

  boolean awaitsInput(TelegramUserId user) {
    return awaiting.containsKey(user);
  }

  /** Kutilayotgan kiritish bekor qilinadi; qoralama tasdiq ekrani uchun qoladi. */
  void cancel(TelegramUserId user) {
    awaiting.remove(user);
  }

  /**
   * @param asNewMessage javob yangi xabar bo'lib chiqsinmi
   */
  Optional<Reply> onButton(TelegramUserId user, String action, boolean asNewMessage) {
    awaiting.remove(user);
    return screenFor(user, action).map(screen -> new Reply(screen, asNewMessage, ""));
  }

  Optional<Screen> onText(TelegramUserId user, String text) {
    Awaiting current = awaiting.remove(user);
    if (current == null) {
      return Optional.empty();
    }
    String input = text.strip();
    Optional<Draft> draft = Optional.ofNullable(drafts.get(user));
    return switch (current) {
      case TITLE -> withTitle(user, input, Optional.empty(), Awaiting.TITLE);
      case EDITED_TITLE ->
          withTitle(user, input, draft.flatMap(Draft::description), Awaiting.EDITED_TITLE);
      case DESCRIPTION, EDITED_DESCRIPTION ->
          draft.isEmpty()
              ? Optional.of(TaskScreens.noDraft())
              : review(user, new Draft(draft.get().title(), Optional.of(input)));
    };
  }

  private Optional<Screen> screenFor(TelegramUserId user, String action) {
    return switch (action) {
      case Actions.TASKS -> describe(tasks.overview(user));
      case Actions.TASK_NEW -> startDraft(user);
      case Actions.TASK_SKIP_DESCRIPTION -> skipDescription(user);
      case Actions.TASK_EDIT_TITLE -> askEdit(user, Awaiting.EDITED_TITLE);
      case Actions.TASK_EDIT_DESCRIPTION -> askEdit(user, Awaiting.EDITED_DESCRIPTION);
      case Actions.TASK_REVIEW ->
          Optional.ofNullable(drafts.get(user))
              .map(draft -> review(user, draft))
              .orElseGet(() -> Optional.of(TaskScreens.noDraft()));
      case Actions.TASK_CONFIRM -> confirm(user);
      case Actions.TASK_DISCARD -> {
        drafts.remove(user);
        yield Optional.of(TaskScreens.discarded());
      }
      default -> withArgument(user, action);
    };
  }

  private Optional<Screen> withArgument(TelegramUserId user, String action) {
    if (action.startsWith(Actions.TASK_GROUP)) {
      Matcher matcher = GROUP.matcher(action.substring(Actions.TASK_GROUP.length()));
      Optional<TaskStatus> status =
          matcher.matches() ? statusOf(matcher.group(1)) : Optional.empty();
      return status.flatMap(
          found -> describe(tasks.list(user, found, Integer.parseInt(matcher.group(2)))));
    }
    if (action.startsWith(Actions.TASK_OPEN)) {
      return id(action, Actions.TASK_OPEN).flatMap(iid -> describe(tasks.open(user, iid)));
    }
    if (action.startsWith(Actions.TASK_CLOSE)) {
      return id(action, Actions.TASK_CLOSE)
          .flatMap(iid -> describe(tasks.setOpen(user, iid, false), "✔️ Task yopildi"));
    }
    if (action.startsWith(Actions.TASK_REOPEN)) {
      return id(action, Actions.TASK_REOPEN)
          .flatMap(iid -> describe(tasks.setOpen(user, iid, true), "↩️ Task qayta ochildi"));
    }
    if (action.startsWith(Actions.TASK_FROM_TRANSCRIPT)) {
      return id(action, Actions.TASK_FROM_TRANSCRIPT).flatMap(id -> fromTranscript(user, id));
    }
    return Optional.empty();
  }

  /** Repo ulanmagan bo'lsa sarlavha so'ralmaydi — avval repo ulash taklif qilinadi. */
  private Optional<Screen> startDraft(TelegramUserId user) {
    return withRepo(
        user,
        linked -> {
          drafts.remove(user);
          awaiting.put(user, Awaiting.TITLE);
          return Optional.of(TaskScreens.askTitle(linked.project()));
        });
  }

  private Optional<Screen> fromTranscript(TelegramUserId user, long journalId) {
    if (!(transcripts.open(user, journalId) instanceof TranscriptsResult.Opened opened)) {
      return Optional.empty();
    }
    String text = opened.transcript().record().transcription().transcript().text();
    NewTask task = NewTask.fromText(text, TITLE_FROM_TEXT);
    return review(user, new Draft(task.title(), Optional.of(task.description())));
  }

  private Optional<Screen> withTitle(
      TelegramUserId user, String title, Optional<String> description, Awaiting step) {
    if (title.isEmpty() || title.length() > NewTask.MAX_TITLE_LENGTH) {
      awaiting.put(user, step);
      return Optional.of(new Screen(INVALID_TITLE, List.of(List.of(BotScreens.CANCEL))));
    }
    Draft draft = new Draft(title, description);
    if (description.isPresent()) {
      return review(user, draft);
    }
    drafts.put(user, draft);
    awaiting.put(user, Awaiting.DESCRIPTION);
    return Optional.of(TaskScreens.askDescription());
  }

  private Optional<Screen> skipDescription(TelegramUserId user) {
    Draft draft = drafts.get(user);
    return draft == null
        ? Optional.of(TaskScreens.noDraft())
        : review(user, new Draft(draft.title(), Optional.of("")));
  }

  private Optional<Screen> askEdit(TelegramUserId user, Awaiting step) {
    Draft draft = drafts.get(user);
    if (draft == null) {
      return Optional.of(TaskScreens.noDraft());
    }
    awaiting.put(user, step);
    return step == Awaiting.EDITED_TITLE
        ? Optional.of(TaskScreens.askEdit("sarlavha", draft.title()))
        : Optional.of(TaskScreens.askEdit("tavsif", draft.description().orElse("")));
  }

  /** Qoralama saqlanadi va tasdiqlash ekrani ko'rsatiladi. */
  private Optional<Screen> review(TelegramUserId user, Draft draft) {
    return withRepo(
        user,
        linked -> {
          drafts.put(user, draft);
          return Optional.of(
              TaskScreens.confirm(draft.task(), linked.project(), linked.link().repo()));
        });
  }

  private Optional<Screen> confirm(TelegramUserId user) {
    Draft draft = drafts.get(user);
    if (draft == null) {
      return Optional.of(TaskScreens.noDraft());
    }
    TasksResult result = tasks.create(user, draft.task());
    if (result instanceof TasksResult.Created) {
      drafts.remove(user, draft);
    }
    return describe(result);
  }

  /** Bazadagi bog'lanish bo'yicha (GitLab'ga so'rovsiz) — sarlavha so'rashdan oldin. */
  private Optional<Screen> withRepo(
      TelegramUserId user, Function<RepoLinkResult.Linked, Optional<Screen>> action) {
    return switch (repos.show(user)) {
      case RepoLinkResult.Linked linked -> action.apply(linked);
      case RepoLinkResult.NotLinked notLinked ->
          ConnectionScreens.unavailable(new RepoUnavailable.NotLinked(notLinked.project()));
      case RepoLinkResult.NoActiveProject() ->
          ConnectionScreens.unavailable(new RepoUnavailable.NoActiveProject());
      default -> Optional.empty();
    };
  }

  private static Optional<Screen> describe(TasksResult result) {
    return switch (result) {
      case TasksResult.Overview overview -> Optional.of(TaskScreens.overview(overview));
      case TasksResult.Page page -> Optional.of(TaskScreens.page(page));
      case TasksResult.Opened opened -> Optional.of(TaskScreens.opened(opened));
      case TasksResult.Created created -> Optional.of(TaskScreens.created(created));
      case RepoUnavailable unavailable -> ConnectionScreens.unavailable(unavailable);
    };
  }

  /** Xabar faqat amal haqiqatan bajarilgan bo'lsa (xato ekraniga emas). */
  private static Optional<Screen> describe(TasksResult result, String notice) {
    return describe(result)
        .map(screen -> result instanceof TasksResult.Opened ? screen.withNotice(notice) : screen);
  }

  private static Optional<TaskStatus> statusOf(String name) {
    try {
      return Optional.of(TaskStatus.valueOf(name));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static Optional<Long> id(String action, String prefix) {
    String argument = action.substring(prefix.length());
    return ID.matcher(argument).matches()
        ? Optional.of(Long.parseLong(argument))
        : Optional.empty();
  }
}
