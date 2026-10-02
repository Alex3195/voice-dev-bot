package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.application.port.in.ChangeLanguageUseCase;
import com.alex.voicedevbot.application.port.in.GlossaryCommandResult;
import com.alex.voicedevbot.application.port.in.LanguageCommandResult;
import com.alex.voicedevbot.application.port.in.ManageGlossaryUseCase;
import com.alex.voicedevbot.application.port.in.ManageProjectsUseCase;
import com.alex.voicedevbot.application.port.in.ProjectCommandResult;
import com.alex.voicedevbot.application.port.in.ProjectCommandResult.ProjectSummary;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Matnli buyruqlarni ({@code /project}, {@code /glossary}, ...) use-case'larga tarjima qiladi.
 *
 * <p>Whitelist'dan tashqaridagi user'ga hech qanday javob qaytarilmaydi ({@link Optional#empty()}),
 * hatto {@code /help}ga ham.
 */
public class TelegramCommands {

  /** Telegram menyusi ("/" bosilganda) — buyruq va qisqa tavsif. */
  static final List<BotMenuItem> MENU =
      List.of(
          new BotMenuItem("help", "Yordam va sozlamalar"),
          new BotMenuItem("project", "Projectlar ro'yxati yoki tanlash"),
          new BotMenuItem("addproject", "Yangi project qo'shish"),
          new BotMenuItem("glossary", "Faol project lug'ati"),
          new BotMenuItem("lang", "Nutq tili (uz, kk, ru...)"));

  /** Telegram HTML; {@code %s} — joriy til kodi. */
  static final String HELP =
      """
      🎙 <b>Voice Dev Bot</b>
      Ovoz, audio fayl yoki video yuboring — matnga aylantiraman.

      📁 <b>Projectlar</b>
      /project — ro'yxat
      /project &lt;nom&gt; — faol projectni tanlash
      /addproject &lt;nom&gt; — yangi project

      📖 <b>Lug'at</b> <i>(faol project)</i>
      /glossary — ko'rish
      /glossary add &lt;atama&gt;, &lt;atama&gt; — qo'shish
      /glossary remove &lt;atama&gt; — o'chirish

      🌐 <b>Nutq tili:</b> %s
      /lang &lt;kod&gt; — o'zgartirish (uz, kk, ru...)""";

  static final String INVALID_INPUT = "⚠️ Noto'g'ri qiymat. Yordam: /help";

  private final ManageProjectsUseCase projects;
  private final ManageGlossaryUseCase glossary;
  private final ChangeLanguageUseCase language;

  public TelegramCommands(
      ManageProjectsUseCase projects,
      ManageGlossaryUseCase glossary,
      ChangeLanguageUseCase language) {
    this.projects = Objects.requireNonNull(projects, "projects");
    this.glossary = Objects.requireNonNull(glossary, "glossary");
    this.language = Objects.requireNonNull(language, "language");
  }

  static boolean isCommand(String text) {
    return text != null && text.startsWith("/");
  }

  /** Javob matni; javob berilmasligi kerak bo'lsa (begona user, noma'lum buyruq) — bo'sh. */
  Optional<String> handle(TelegramUserId user, String text) {
    Command command = Command.parse(text);
    try {
      return switch (command.name()) {
        case "start", "help" -> help(user);
        case "project" -> project(user, command.argument());
        case "addproject" -> addProject(user, command.argument());
        case "glossary" -> glossary(user, command.argument());
        case "lang" -> language(user, command.argument());
        default -> Optional.empty();
      };
    } catch (IllegalArgumentException e) {
      return Optional.of(INVALID_INPUT);
    }
  }

  private Optional<String> help(TelegramUserId user) {
    return switch (language.currentLanguage(user)) {
      case LanguageCommandResult.Current(var current) ->
          reply(HELP.formatted(Html.code(current.code())));
      case LanguageCommandResult.Changed(var changed) ->
          reply(HELP.formatted(Html.code(changed.code())));
      case LanguageCommandResult.AccessDenied() -> Optional.empty();
    };
  }

  private Optional<String> project(TelegramUserId user, String argument) {
    ProjectCommandResult result =
        argument.isEmpty()
            ? projects.listProjects(user)
            : projects.selectProject(user, new ProjectName(argument));
    return describe(result);
  }

  private Optional<String> addProject(TelegramUserId user, String argument) {
    if (argument.isEmpty()) {
      return reply("ℹ️ Foydalanish: /addproject &lt;nom&gt;");
    }
    return describe(projects.addProject(user, new ProjectName(argument)));
  }

  private Optional<String> glossary(TelegramUserId user, String argument) {
    Command sub = Command.parse("/" + argument);
    List<String> terms = splitTerms(sub.argument());
    GlossaryCommandResult result =
        switch (sub.name()) {
          case "" -> glossary.showGlossary(user);
          case "add" -> glossary.addTerms(user, terms);
          case "remove" -> glossary.removeTerms(user, terms);
          default -> throw new IllegalArgumentException("Unknown glossary action " + sub.name());
        };
    return describe(result);
  }

  private Optional<String> language(TelegramUserId user, String argument) {
    LanguageCommandResult result =
        argument.isEmpty()
            ? language.currentLanguage(user)
            : language.changeLanguage(user, new SpeechLanguage(argument));
    return switch (result) {
      case LanguageCommandResult.Current(var current) ->
          reply(
              "🌐 <b>Nutq tili:</b> "
                  + Html.code(current.code())
                  + "\nO'zgartirish: /lang &lt;kod&gt; (uz, kk, ru...)");
      case LanguageCommandResult.Changed(var changed) ->
          reply("✅ Nutq tili o'zgardi: " + Html.code(changed.code()));
      case LanguageCommandResult.AccessDenied() -> Optional.empty();
    };
  }

  private static Optional<String> describe(ProjectCommandResult result) {
    return switch (result) {
      case ProjectCommandResult.Added(var name) ->
          reply(
              "✅ Project qo'shildi va faol qilindi: "
                  + Html.bold(name.value())
                  + "\nEndi atamalar qo'shing: /glossary add &lt;atama&gt;, &lt;atama&gt;");
      case ProjectCommandResult.AlreadyExists(var name) ->
          reply(
              "ℹ️ Bu project allaqachon bor: "
                  + Html.bold(name.value())
                  + "\nTanlash: "
                  + Html.code("/project " + name.value()));
      case ProjectCommandResult.Selected(var name) ->
          reply("▶️ Faol project: " + Html.bold(name.value()));
      case ProjectCommandResult.NotFound(var name) ->
          reply("⚠️ Project topilmadi: " + Html.bold(name.value()) + "\nRo'yxat: /project");
      case ProjectCommandResult.Listed(var summaries) -> reply(listing(summaries));
      case ProjectCommandResult.AccessDenied() -> Optional.empty();
    };
  }

  private static Optional<String> describe(GlossaryCommandResult result) {
    return switch (result) {
      case GlossaryCommandResult.Shown(var project, var terms) ->
          reply(glossaryListing(project, terms));
      case GlossaryCommandResult.NoActiveProject() ->
          reply("⚠️ Avval project tanlang: /project &lt;nom&gt; yoki /addproject &lt;nom&gt;");
      case GlossaryCommandResult.AccessDenied() -> Optional.empty();
    };
  }

  private static String listing(List<ProjectSummary> summaries) {
    if (summaries.isEmpty()) {
      return "📁 Hali project yo'q.\nQo'shish: /addproject &lt;nom&gt;";
    }
    return summaries.stream()
        .map(TelegramCommands::listingLine)
        .collect(
            Collectors.joining(
                "\n", "📁 <b>Projectlar</b>\n\n", "\n\nTanlash: /project &lt;nom&gt;"));
  }

  private static String listingLine(ProjectSummary summary) {
    String name =
        summary.active() ? Html.bold(summary.name().value()) : Html.escape(summary.name().value());
    return (summary.active() ? "▶️ " : "▫️ ") + name + " — " + summary.termCount() + " atama";
  }

  private static String glossaryListing(ProjectName project, List<String> terms) {
    String title = "📖 " + Html.bold(project.value()) + " lug'ati";
    if (terms.isEmpty()) {
      return title + " bo'sh.\nQo'shish: /glossary add &lt;atama&gt;, &lt;atama&gt;";
    }
    String list = terms.stream().map(Html::code).collect(Collectors.joining(" · "));
    return title + " — " + terms.size() + " atama\n\n" + list;
  }

  private static List<String> splitTerms(String text) {
    return Arrays.stream(text.split(",")).map(String::strip).filter(t -> !t.isEmpty()).toList();
  }

  private static Optional<String> reply(String text) {
    return Optional.of(text);
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

  /** Telegram menyusidagi bitta buyruq. */
  record BotMenuItem(String command, String description) {}
}
