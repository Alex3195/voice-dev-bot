package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.adapter.in.telegram.BotConversation.Reply;
import com.alex.voicedevbot.application.port.in.BrowseTranscriptsUseCase;
import com.alex.voicedevbot.application.port.in.DraftTaskUseCase;
import com.alex.voicedevbot.application.port.in.GlossaryCommandResult;
import com.alex.voicedevbot.application.port.in.LanguageModelProblem;
import com.alex.voicedevbot.application.port.in.LinkRepoUseCase;
import com.alex.voicedevbot.application.port.in.ManageGlossaryUseCase;
import com.alex.voicedevbot.application.port.in.ManageTasksUseCase;
import com.alex.voicedevbot.application.port.in.RepoLinkResult;
import com.alex.voicedevbot.application.port.in.RepoUnavailable;
import com.alex.voicedevbot.application.port.in.TaskDraftResult;
import com.alex.voicedevbot.application.port.in.TasksResult;
import com.alex.voicedevbot.application.port.in.TranscriptsResult;
import com.alex.voicedevbot.domain.NewTask;
import com.alex.voicedevbot.domain.TaskStatus;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faol project tasklari ({@code ✅ Tasklar}): holat bo'yicha guruhlar, task ichida yopish/qayta
 * ochish, yangi task (qo'lda yoki transkriptdan). Tugmalari {@link Actions#TASKS} bilan boshlanadi.
 *
 * <p>Yangi task qoralamasi va kutilayotgan kiritish xotirada: bot qayta ishga tushsa yo'qoladi.
 * Tasdiqsiz ({@code ✅ Yaratish}) hech narsa yaratilmaydi. Claude qoralamasidan task yaratilsa,
 * tuzatilgan transkript tasdiqlangan deb belgilanadi.
 */
public class TaskDialog {

  /** Transkriptdan olinadigan sarlavha uzunligi. */
  static final int TITLE_FROM_TEXT = 80;

  /** Claude chaqirilayotganda tugma o'rnida va bildirishnomada. */
  static final String DRAFTING = "⏳ Claude qoralama tuzmoqda…";

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

  /**
   * Sarlavha kiritilgach, tavsif hali bo'lmasligi mumkin.
   *
   * @param claude qoralamani Claude tuzgan bo'lsa — uning izohlari (tahrirdan keyin ham qoladi)
   * @param transcript qaysi transkriptdan tuzilgan (qo'lda yozilgan bo'lsa — bo'sh)
   */
  private record Draft(
      String title,
      Optional<String> description,
      Optional<ClaudeNotes> claude,
      OptionalLong transcript) {

    Draft(String title, Optional<String> description) {
      this(title, description, Optional.empty(), OptionalLong.empty());
    }

    NewTask task() {
      return new NewTask(title, description.orElse(""));
    }

    Draft withTitle(String newTitle) {
      return new Draft(newTitle, description, claude, transcript);
    }

    Draft withDescription(String newDescription) {
      return new Draft(title, Optional.of(newDescription), claude, transcript);
    }

    Draft withoutSuggestion(String term) {
      return new Draft(title, description, claude.map(notes -> notes.without(term)), transcript);
    }

    /** Shu transkriptdan Claude allaqachon tuzgan — qayta chaqirish shart emas. */
    boolean draftedByClaudeFrom(long journalId) {
      return claude.isPresent() && transcript.equals(OptionalLong.of(journalId));
    }
  }

  private final ManageTasksUseCase tasks;
  private final LinkRepoUseCase repos;
  private final BrowseTranscriptsUseCase transcripts;
  private final DraftTaskUseCase drafter;
  private final ManageGlossaryUseCase glossary;
  private final Map<TelegramUserId, Awaiting> awaiting = new ConcurrentHashMap<>();
  private final Map<TelegramUserId, Draft> drafts = new ConcurrentHashMap<>();

  /** Claude javobini kutayotgan foydalanuvchilar — qayta bosish ikkinchi so'rov yubormaydi. */
  private final Map<TelegramUserId, Long> drafting = new ConcurrentHashMap<>();

  public TaskDialog(
      ManageTasksUseCase tasks,
      LinkRepoUseCase repos,
      BrowseTranscriptsUseCase transcripts,
      DraftTaskUseCase drafter,
      ManageGlossaryUseCase glossary) {
    this.tasks = Objects.requireNonNull(tasks, "tasks");
    this.repos = Objects.requireNonNull(repos, "repos");
    this.transcripts = Objects.requireNonNull(transcripts, "transcripts");
    this.drafter = Objects.requireNonNull(drafter, "drafter");
    this.glossary = Objects.requireNonNull(glossary, "glossary");
  }

  static boolean handles(String action) {
    return action.equals(Actions.TASKS) || action.startsWith(Actions.TASK_PREFIX);
  }

  /** Claude'ni chaqiradigan (bir necha soniya ishlaydigan) tugma. */
  static boolean isSlow(String action) {
    return action.startsWith(Actions.TASK_FROM_TRANSCRIPT);
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
      case EDITED_TITLE -> editedTitle(user, draft, input);
      case DESCRIPTION, EDITED_DESCRIPTION ->
          draft.isEmpty()
              ? Optional.of(TaskScreens.noDraft())
              : review(user, draft.get().withDescription(input));
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
    if (action.startsWith(Actions.TASK_ADD_TERM)) {
      return id(action, Actions.TASK_ADD_TERM).flatMap(index -> addTerm(user, index));
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

  /**
   * Claude bilan; u o'chiq yoki ishlamasa — oddiy qoralama (birinchi gap sarlavha). Repo ulanmagan
   * bo'lsa Claude chaqirilmaydi — avval repo ulash taklif qilinadi.
   *
   * <p>Tugma qayta bosilsa token sarflanmaydi: shu transkriptdan Claude tuzgan qoralama bo'lsa — u
   * ko'rsatiladi, Claude hali ishlayotgan bo'lsa — javob yo'q.
   */
  private Optional<Screen> fromTranscript(TelegramUserId user, long journalId) {
    Draft existing = drafts.get(user);
    if (existing != null && existing.draftedByClaudeFrom(journalId)) {
      return review(user, existing);
    }
    if (drafting.putIfAbsent(user, journalId) != null) {
      return Optional.empty();
    }
    try {
      return withRepo(user, linked -> draftFromTranscript(user, journalId));
    } finally {
      drafting.remove(user);
    }
  }

  private Optional<Screen> draftFromTranscript(TelegramUserId user, long journalId) {
    return switch (drafter.fromTranscript(user, journalId)) {
      case TaskDraftResult.Drafted drafted -> {
        NewTask task = drafted.draft().toNewTask();
        yield review(
            user,
            new Draft(
                task.title(),
                Optional.of(task.description()),
                Optional.of(ClaudeNotes.of(drafted, journalId)),
                OptionalLong.of(journalId)));
      }
      case TaskDraftResult.Failed(var problem)
          when problem == LanguageModelProblem.NOT_CONFIGURED ->
          plainDraft(user, journalId);
      case TaskDraftResult.Failed(var problem) ->
          plainDraft(user, journalId)
              .map(
                  screen ->
                      screen.withNotice(
                          ModelScreens.problem(problem)
                              + "\nOddiy qoralama (birinchi gap — sarlavha):"));
      case TaskDraftResult.NotFound() -> plainDraft(user, journalId);
      case TaskDraftResult.AccessDenied() -> Optional.empty();
    };
  }

  private Optional<Screen> plainDraft(TelegramUserId user, long journalId) {
    if (!(transcripts.open(user, journalId) instanceof TranscriptsResult.Opened opened)) {
      return Optional.empty();
    }
    String text = opened.transcript().record().transcription().transcript().text();
    NewTask task = NewTask.fromText(text, TITLE_FROM_TEXT);
    return review(
        user,
        new Draft(
            task.title(),
            Optional.of(task.description()),
            Optional.empty(),
            OptionalLong.of(journalId)));
  }

  /** Claude taklif qilgan atama faol project lug'atiga qo'shiladi, qoralama o'zgarmaydi. */
  private Optional<Screen> addTerm(TelegramUserId user, long index) {
    Draft draft = drafts.get(user);
    if (draft == null) {
      return Optional.of(TaskScreens.noDraft());
    }
    List<String> terms = draft.claude().map(ClaudeNotes::suggestedTerms).orElse(List.of());
    if (index >= terms.size()) {
      return review(user, draft);
    }
    String term = terms.get((int) index);
    if (!(glossary.addTerms(user, List.of(term)) instanceof GlossaryCommandResult.Shown)) {
      return review(user, draft);
    }
    return review(user, draft.withoutSuggestion(term))
        .map(screen -> screen.withNotice("📖 Lug'atga qo'shildi: " + Html.bold(term)));
  }

  private Optional<Screen> editedTitle(TelegramUserId user, Optional<Draft> draft, String title) {
    if (draft.isEmpty()) {
      return withTitle(user, title, Optional.empty(), Awaiting.EDITED_TITLE);
    }
    if (title.isEmpty() || title.length() > NewTask.MAX_TITLE_LENGTH) {
      awaiting.put(user, Awaiting.EDITED_TITLE);
      return Optional.of(new Screen(INVALID_TITLE, List.of(List.of(BotScreens.CANCEL))));
    }
    return review(user, draft.get().withTitle(title));
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
              TaskScreens.confirm(
                  draft.task(), linked.project(), linked.link().repo(), draft.claude()));
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
      draft
          .claude()
          .flatMap(notes -> notes.correctedIn().stream().boxed().findFirst())
          .ifPresent(journalId -> drafter.confirmCorrection(user, journalId));
    }
    return describe(result);
  }

  /** Bazadagi bog'lanish bo'yicha (xizmatga so'rovsiz) — sarlavha so'rashdan oldin. */
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
