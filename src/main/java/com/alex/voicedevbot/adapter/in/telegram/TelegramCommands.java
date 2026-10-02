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

  static final String HELP =
      """
      Ovoz, audio fayl yoki video yuboring — matnga aylantiraman.

      /project — projectlar ro'yxati
      /project <nom> — faol projectni tanlash
      /addproject <nom> — yangi project
      /glossary — faol project lug'ati
      /glossary add <atama>, <atama> — atama qo'shish
      /glossary remove <atama> — atamani o'chirish
      /lang <kod> — nutq tili (uz, kk, ru...)

      Hozirgi til: %s""";
  static final String INVALID_INPUT = "Noto'g'ri qiymat. Yordam: /help";

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
      case LanguageCommandResult.Current(var current) -> reply(HELP.formatted(current.code()));
      case LanguageCommandResult.Changed(var changed) -> reply(HELP.formatted(changed.code()));
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
      return reply("Foydalanish: /addproject <nom>");
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
          reply("Nutq tili: " + current.code() + ". O'zgartirish: /lang <kod>");
      case LanguageCommandResult.Changed(var changed) ->
          reply("Nutq tili o'zgardi: " + changed.code());
      case LanguageCommandResult.AccessDenied() -> Optional.empty();
    };
  }

  private static Optional<String> describe(ProjectCommandResult result) {
    return switch (result) {
      case ProjectCommandResult.Added(var name) ->
          reply("Project qo'shildi va faol qilindi: " + name.value());
      case ProjectCommandResult.AlreadyExists(var name) ->
          reply(
              "Bu project allaqachon bor: " + name.value() + ". Tanlash: /project " + name.value());
      case ProjectCommandResult.Selected(var name) -> reply("Faol project: " + name.value());
      case ProjectCommandResult.NotFound(var name) ->
          reply("Project topilmadi: " + name.value() + ". Ro'yxat: /project");
      case ProjectCommandResult.Listed(var summaries) -> reply(listing(summaries));
      case ProjectCommandResult.AccessDenied() -> Optional.empty();
    };
  }

  private static Optional<String> describe(GlossaryCommandResult result) {
    return switch (result) {
      case GlossaryCommandResult.Shown(var project, var terms) ->
          reply(
              terms.isEmpty()
                  ? project.value() + " lug'ati bo'sh. Qo'shish: /glossary add <atama>, <atama>"
                  : project.value()
                      + " lug'ati ("
                      + terms.size()
                      + "):\n"
                      + String.join(", ", terms));
      case GlossaryCommandResult.NoActiveProject() ->
          reply("Avval project tanlang: /project <nom> yoki /addproject <nom>");
      case GlossaryCommandResult.AccessDenied() -> Optional.empty();
    };
  }

  private static String listing(List<ProjectSummary> summaries) {
    if (summaries.isEmpty()) {
      return "Hali project yo'q. Qo'shish: /addproject <nom>";
    }
    return summaries.stream()
        .map(s -> (s.active() ? "▶ " : "• ") + s.name().value() + " — " + s.termCount() + " atama")
        .collect(Collectors.joining("\n", "Projectlar:\n", "\n\nTanlash: /project <nom>"));
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
}
