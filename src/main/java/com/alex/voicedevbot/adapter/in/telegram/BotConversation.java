package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.application.port.in.BrowseTranscriptsUseCase;
import com.alex.voicedevbot.application.port.in.ChangeLanguageUseCase;
import com.alex.voicedevbot.application.port.in.GlossaryCommandResult;
import com.alex.voicedevbot.application.port.in.LanguageCommandResult;
import com.alex.voicedevbot.application.port.in.ManageGlossaryUseCase;
import com.alex.voicedevbot.application.port.in.ManageProjectsUseCase;
import com.alex.voicedevbot.application.port.in.ProjectCommandResult;
import com.alex.voicedevbot.application.port.in.ProjectCommandResult.ProjectSummary;
import com.alex.voicedevbot.application.port.in.TranscriptsResult;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.TranscriptFilter;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Foydalanuvchi bilan muloqot: buyruqlar, inline tugmalar va bot so'ragan matn (project nomi,
 * atamalar). Har bir amal use-case orqali o'tadi — whitelist'dan tashqaridagi user hech qanday
 * javob olmaydi ({@link Optional#empty()}).
 *
 * <p>Kutilayotgan kiritish xotirada: bot qayta ishga tushsa yo'qoladi, foydalanuvchi tugmani qayta
 * bosadi.
 */
public class BotConversation {

  /** Telegram menyusi ("/" bosilganda). */
  static final List<BotMenuItem> MENU =
      List.of(
          new BotMenuItem("start", "🏠 Bosh menyu"),
          new BotMenuItem("project", "📁 Projectlar"),
          new BotMenuItem("glossary", "📖 Faol project lug'ati"),
          new BotMenuItem("settings", "⚙️ Sozlamalar"),
          new BotMenuItem("lang", "🌐 Nutq tili"),
          new BotMenuItem("help", "❓ Yordam"));

  static final String INVALID_INPUT = "⚠️ Noto'g'ri qiymat, qaytadan urinib ko'ring.";
  static final String PROJECT_NOT_FOUND = "Project topilmadi";

  /** {@code <project id yoki "-">:<sahifa>} — {@link Actions#transcripts}. */
  private static final Pattern TRANSCRIPTS_ARGUMENT = Pattern.compile("([0-9a-f]+|-):(\\d{1,6})");

  private static final Pattern TRANSCRIPT_ID = Pattern.compile("\\d{1,18}");

  enum Pending {
    PROJECT_NAME,
    TERMS
  }

  private final ManageProjectsUseCase projects;
  private final ManageGlossaryUseCase glossary;
  private final ChangeLanguageUseCase language;
  private final BrowseTranscriptsUseCase transcripts;
  private final GitLabDialog gitLab;
  private final TaskDialog tasks;
  private final DocsDialog docs;
  private final ZoneId zone;
  private final Map<TelegramUserId, Pending> pending = new ConcurrentHashMap<>();

  /**
   * @param zone transkript vaqtlari qaysi vaqt mintaqasida ko'rsatiladi
   */
  public BotConversation(
      ManageProjectsUseCase projects,
      ManageGlossaryUseCase glossary,
      ChangeLanguageUseCase language,
      BrowseTranscriptsUseCase transcripts,
      GitLabDialog gitLab,
      TaskDialog tasks,
      DocsDialog docs,
      ZoneId zone) {
    this.projects = Objects.requireNonNull(projects, "projects");
    this.glossary = Objects.requireNonNull(glossary, "glossary");
    this.language = Objects.requireNonNull(language, "language");
    this.transcripts = Objects.requireNonNull(transcripts, "transcripts");
    this.gitLab = Objects.requireNonNull(gitLab, "gitLab");
    this.tasks = Objects.requireNonNull(tasks, "tasks");
    this.docs = Objects.requireNonNull(docs, "docs");
    this.zone = Objects.requireNonNull(zone, "zone");
  }

  /** Matnli xabar: buyruq, bot so'ragan qiymat yoki oddiy matn (bosh menyu ko'rsatiladi). */
  Optional<Screen> onText(TelegramUserId user, String text) {
    if (!isCommand(text) && gitLab.awaitsInput(user)) {
      return gitLab.onText(user, text);
    }
    if (!isCommand(text) && tasks.awaitsInput(user)) {
      return tasks.onText(user, text);
    }
    gitLab.cancel(user);
    tasks.cancel(user);
    Pending awaited = pending.remove(user);
    try {
      if (isCommand(text)) {
        return command(user, Command.parse(text));
      }
      return awaited == null ? home(user) : input(user, awaited, text.strip());
    } catch (IllegalArgumentException e) {
      if (awaited != null) {
        pending.put(user, awaited);
      }
      return Optional.of(Screen.text(INVALID_INPUT));
    }
  }

  /** Inline tugma bosildi. */
  Optional<Reply> onButton(TelegramUserId user, String data) {
    pending.remove(user);
    gitLab.cancel(user);
    tasks.cancel(user);
    boolean asNewMessage = data.startsWith(Actions.NEW_MESSAGE);
    String action = asNewMessage ? data.substring(Actions.NEW_MESSAGE.length()) : data;
    if (action.startsWith(Actions.SELECT_PROJECT) && !action.equals(Actions.NEW_PROJECT)) {
      return selectProjectById(user, action.substring(Actions.SELECT_PROJECT.length()));
    }
    if (action.startsWith(Actions.REMOVE_TERM)) {
      return removeTermById(user, action.substring(Actions.REMOVE_TERM.length()));
    }
    if (action.startsWith(Actions.SET_LANGUAGE)) {
      return changeLanguage(user, action.substring(Actions.SET_LANGUAGE.length()));
    }
    if (action.equals(Actions.GITLAB) || action.startsWith(Actions.GITLAB_PREFIX)) {
      return gitLab.onButton(user, action);
    }
    if (TaskDialog.handles(action)) {
      return tasks.onButton(user, action, asNewMessage);
    }
    if (DocsDialog.handles(action)) {
      return docs.onButton(user, action);
    }
    if (action.startsWith(Actions.TRANSCRIPTS)) {
      return listTranscripts(user, action.substring(Actions.TRANSCRIPTS.length()));
    }
    if (action.startsWith(Actions.OPEN_TRANSCRIPT)) {
      return openTranscript(user, action.substring(Actions.OPEN_TRANSCRIPT.length()));
    }
    return screenFor(user, action).map(screen -> new Reply(screen, asNewMessage, ""));
  }

  /** Bot token kutyapti — foydalanuvchi yozgan xabar chatdan o'chirilishi kerak. */
  boolean expectsSecret(TelegramUserId user) {
    return gitLab.expectsSecret(user);
  }

  /**
   * Transkript ostida faol project va til; foydalanuvchi allaqachon whitelist'dan o'tgan.
   *
   * @param journalId jurnaldagi raqam — bo'lsa, {@code ✅ Task yaratish} tugmasi chiqadi
   */
  Screen transcript(TelegramUserId user, String text, OptionalLong journalId) {
    return context(user)
        .map(
            context -> BotScreens.transcript(text, context.active(), context.language(), journalId))
        .orElseGet(() -> Screen.text("📝 <b>Matn</b>\n\n" + Html.escape(text)));
  }

  static boolean isCommand(String text) {
    return text != null && text.startsWith("/");
  }

  private Optional<Screen> screenFor(TelegramUserId user, String action) {
    return switch (action) {
      case Actions.HOME, Actions.CANCEL -> home(user);
      case Actions.HELP -> context(user).map(context -> BotScreens.help());
      case Actions.PROJECTS -> projectsScreen(user);
      case Actions.NEW_PROJECT -> askProjectName(user);
      case Actions.GLOSSARY -> glossaryScreen(user, false);
      case Actions.REMOVE_MODE -> glossaryScreen(user, true);
      case Actions.ADD_TERMS -> askTerms(user);
      case Actions.LANGUAGES ->
          context(user).map(context -> BotScreens.languages(context.language()));
      case Actions.SETTINGS ->
          context(user).map(context -> BotScreens.settings(context.language()));
      default -> Optional.empty();
    };
  }

  private Optional<Screen> command(TelegramUserId user, Command command) {
    String argument = command.argument();
    return switch (command.name()) {
      case "start" -> home(user);
      case "help" -> screenFor(user, Actions.HELP);
      case "settings" -> screenFor(user, Actions.SETTINGS);
      case "project" ->
          argument.isEmpty() ? projectsScreen(user) : selectByName(user, new ProjectName(argument));
      case "addproject" ->
          argument.isEmpty() ? askProjectName(user) : addProject(user, new ProjectName(argument));
      case "glossary" -> glossaryCommand(user, Command.parse("/" + argument));
      case "lang" ->
          argument.isEmpty()
              ? screenFor(user, Actions.LANGUAGES)
              : changeLanguage(user, argument).map(Reply::screen);
      default -> Optional.empty();
    };
  }

  private Optional<Screen> glossaryCommand(TelegramUserId user, Command sub) {
    List<String> terms = splitTerms(sub.argument());
    return switch (sub.name()) {
      case "" -> glossaryScreen(user, false);
      case "add" -> terms.isEmpty() ? askTerms(user) : addTerms(user, terms);
      case "remove" ->
          terms.isEmpty()
              ? glossaryScreen(user, true)
              : describe(glossary.removeTerms(user, terms), false);
      default -> throw new IllegalArgumentException("Unknown glossary action " + sub.name());
    };
  }

  private Optional<Screen> input(TelegramUserId user, Pending awaited, String text) {
    return switch (awaited) {
      case PROJECT_NAME -> addProject(user, new ProjectName(text));
      case TERMS -> addTerms(user, splitTerms(text));
    };
  }

  private Optional<Screen> home(TelegramUserId user) {
    return context(user).map(context -> BotScreens.home(context.active(), context.language()));
  }

  private Optional<Screen> projectsScreen(TelegramUserId user) {
    return context(user).map(context -> BotScreens.projects(context.projects()));
  }

  private Optional<Screen> askProjectName(TelegramUserId user) {
    return context(user)
        .map(
            context -> {
              pending.put(user, Pending.PROJECT_NAME);
              return BotScreens.askProjectName();
            });
  }

  private Optional<Screen> addProject(TelegramUserId user, ProjectName name) {
    return switch (projects.addProject(user, name)) {
      case ProjectCommandResult.Added(var added) ->
          Optional.of(
              BotScreens.withRepoOffer(BotScreens.glossary(added, List.of()))
                  .withNotice("✅ Project qo'shildi va faol qilindi: " + Html.bold(added.value())));
      case ProjectCommandResult.AlreadyExists(var existing) ->
          projectsScreen(user)
              .map(
                  s ->
                      s.withNotice("ℹ️ Bu project allaqachon bor: " + Html.bold(existing.value())));
      default -> Optional.empty();
    };
  }

  private Optional<Screen> selectByName(TelegramUserId user, ProjectName name) {
    return switch (projects.selectProject(user, name)) {
      case ProjectCommandResult.Selected(var selected) ->
          home(user).map(s -> s.withNotice("✅ Faol project: " + Html.bold(selected.value())));
      case ProjectCommandResult.NotFound(var missing) ->
          projectsScreen(user)
              .map(s -> s.withNotice("⚠️ Project topilmadi: " + Html.bold(missing.value())));
      default -> Optional.empty();
    };
  }

  /** Project kartochkasi: bosilgan project faol qilinadi. */
  private Optional<Reply> selectProjectById(TelegramUserId user, String id) {
    Optional<ProjectName> name = context(user).flatMap(context -> findById(context.projects(), id));
    if (name.isEmpty()) {
      return projectsScreen(user).map(s -> new Reply(s, false, PROJECT_NOT_FOUND));
    }
    projects.selectProject(user, name.get());
    return context(user)
        .flatMap(context -> context.projects().stream().filter(ProjectSummary::active).findFirst())
        .map(summary -> BotScreens.projectCard(summary, gitLab.repoButton(user)))
        .map(card -> new Reply(card, false, ""));
  }

  private Optional<Reply> listTranscripts(TelegramUserId user, String argument) {
    Matcher matcher = TRANSCRIPTS_ARGUMENT.matcher(argument);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    String scope = matcher.group(1);
    int page = Integer.parseInt(matcher.group(2));
    Optional<ProjectName> project = Optional.empty();
    if (!scope.equals(Actions.WITHOUT_PROJECT)) {
      Optional<Context> context = context(user);
      if (context.isEmpty()) {
        return Optional.empty();
      }
      project = findById(context.get().projects(), scope);
      if (project.isEmpty()) {
        return projectsScreen(user).map(s -> new Reply(s, false, PROJECT_NOT_FOUND));
      }
    }
    TranscriptFilter filter =
        project
            .<TranscriptFilter>map(TranscriptFilter.OfProject::new)
            .orElseGet(TranscriptFilter.WithoutProject::new);
    if (!(transcripts.list(user, filter, page) instanceof TranscriptsResult.Page result)) {
      return Optional.empty();
    }
    return Optional.of(
        new Reply(BotScreens.transcripts(result, project.orElse(null), zone), false, ""));
  }

  private Optional<Reply> openTranscript(TelegramUserId user, String id) {
    if (!TRANSCRIPT_ID.matcher(id).matches()) {
      return Optional.empty();
    }
    return switch (transcripts.open(user, Long.parseLong(id))) {
      case TranscriptsResult.Opened(var entry) ->
          Optional.of(new Reply(BotScreens.transcriptEntry(entry, zone), true, ""));
      case TranscriptsResult.NotFound() ->
          home(user).map(s -> new Reply(s, true, "Transkript topilmadi"));
      case TranscriptsResult.Page ignored -> Optional.empty();
      case TranscriptsResult.AccessDenied() -> Optional.empty();
    };
  }

  private Optional<Screen> askTerms(TelegramUserId user) {
    return switch (glossary.showGlossary(user)) {
      case GlossaryCommandResult.Shown(var project, var terms) -> {
        pending.put(user, Pending.TERMS);
        yield Optional.of(BotScreens.askTerms(project));
      }
      case GlossaryCommandResult.NoActiveProject() -> Optional.of(BotScreens.noActiveProject());
      case GlossaryCommandResult.AccessDenied() -> Optional.empty();
    };
  }

  private Optional<Screen> addTerms(TelegramUserId user, List<String> terms) {
    return describe(glossary.addTerms(user, terms), false)
        .map(s -> s.withNotice("✅ Lug'at yangilandi"));
  }

  private Optional<Screen> glossaryScreen(TelegramUserId user, boolean removeMode) {
    return describe(glossary.showGlossary(user), removeMode);
  }

  private Optional<Reply> removeTermById(TelegramUserId user, String id) {
    if (!(glossary.showGlossary(user) instanceof GlossaryCommandResult.Shown shown)) {
      return glossaryScreen(user, true).map(s -> new Reply(s, false, ""));
    }
    Optional<String> term =
        shown.terms().stream()
            .filter(t -> Actions.removeTerm(t).equals(Actions.REMOVE_TERM + id))
            .findFirst();
    term.ifPresent(t -> glossary.removeTerms(user, List.of(t)));
    String toast = term.map(t -> "❌ O'chirildi: " + t).orElse("Atama topilmadi");
    return glossaryScreen(user, true).map(s -> new Reply(s, false, toast));
  }

  private Optional<Reply> changeLanguage(TelegramUserId user, String code) {
    return switch (language.changeLanguage(user, new SpeechLanguage(code))) {
      case LanguageCommandResult.Changed(var changed) ->
          Optional.of(
              new Reply(
                  BotScreens.languages(changed),
                  false,
                  "✅ Nutq tili: " + BotScreens.languageLabel(changed)));
      default -> Optional.empty();
    };
  }

  private static Optional<Screen> describe(GlossaryCommandResult result, boolean removeMode) {
    return switch (result) {
      case GlossaryCommandResult.Shown(var project, var terms) ->
          Optional.of(
              removeMode
                  ? BotScreens.removeTerms(project, terms)
                  : BotScreens.glossary(project, terms));
      case GlossaryCommandResult.NoActiveProject() -> Optional.of(BotScreens.noActiveProject());
      case GlossaryCommandResult.AccessDenied() -> Optional.empty();
    };
  }

  /** Projectlar ro'yxati va til; whitelist'dan tashqarida bo'lsa — bo'sh. */
  private Optional<Context> context(TelegramUserId user) {
    if (!(projects.listProjects(user) instanceof ProjectCommandResult.Listed listed)) {
      return Optional.empty();
    }
    if (!(language.currentLanguage(user) instanceof LanguageCommandResult.Current current)) {
      return Optional.empty();
    }
    return Optional.of(new Context(listed.projects(), current.language()));
  }

  private static Optional<ProjectName> findById(List<ProjectSummary> summaries, String id) {
    return summaries.stream()
        .map(ProjectSummary::name)
        .filter(name -> Actions.idOf(name.key()).equals(id))
        .findFirst();
  }

  private static List<String> splitTerms(String text) {
    return Arrays.stream(text.split(",")).map(String::strip).filter(t -> !t.isEmpty()).toList();
  }

  /**
   * Tugma bosilgandagi javob.
   *
   * @param asNewMessage {@code true} — yangi xabar; {@code false} — bosilgan xabarni tahrirlash
   * @param toast tugma ustida qisqa bildirishnoma; bo'sh bo'lsa ko'rsatilmaydi
   */
  record Reply(Screen screen, boolean asNewMessage, String toast) {}

  /** Telegram menyusidagi bitta buyruq. */
  record BotMenuItem(String command, String description) {}

  private record Context(List<ProjectSummary> projects, SpeechLanguage language) {

    Optional<ProjectName> active() {
      return projects.stream().filter(ProjectSummary::active).map(ProjectSummary::name).findFirst();
    }
  }

  /** {@code /nom@bot argument} → nom (kichik harfda, bot nomisiz) va argument. */
  record Command(String name, String argument) {

    static Command parse(String text) {
      String body = text.strip().substring(1);
      int space = body.indexOf(' ');
      String head = space < 0 ? body : body.substring(0, space);
      String argument = space < 0 ? "" : body.substring(space + 1).strip();
      int mention = head.indexOf('@');
      String name = mention < 0 ? head : head.substring(0, mention);
      return new Command(name.toLowerCase(Locale.ROOT), argument);
    }
  }
}
