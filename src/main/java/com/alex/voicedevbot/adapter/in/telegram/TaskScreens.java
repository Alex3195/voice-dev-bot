package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.port.in.ManageTasksUseCase;
import com.alex.voicedevbot.application.port.in.TasksResult;
import com.alex.voicedevbot.domain.MergeRequest;
import com.alex.voicedevbot.domain.NewTask;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.Repo;
import com.alex.voicedevbot.domain.Task;
import com.alex.voicedevbot.domain.TaskStatus;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Tasklar (Issue) ekranlari. Holatsiz, faqat ko'rinish. */
final class TaskScreens {

  /** Uzun tavsif ekranga sig'ishi uchun qisqartiriladi; to'liq matn xizmatning o'zida. */
  static final int DESCRIPTION_PREVIEW = 2500;

  private static final int GROUPS_PER_ROW = 2;
  private static final int TITLE_PREVIEW = 40;
  private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private TaskScreens() {}

  static String icon(TaskStatus status) {
    return switch (status) {
      case OVERDUE -> "⏰";
      case OPEN -> "🟢";
      case IN_REVIEW -> "🔀";
      case DONE -> "✅";
      case CLOSED -> "⚪";
    };
  }

  static String label(TaskStatus status) {
    return icon(status)
        + " "
        + switch (status) {
          case OVERDUE -> "Muddati o'tgan";
          case OPEN -> "Ochiq";
          case IN_REVIEW -> "MR ochilgan";
          case DONE -> "Bajarilgan";
          case CLOSED -> "Yopiq";
        };
  }

  static Screen overview(TasksResult.Overview overview) {
    StringBuilder html =
        new StringBuilder("✅ ")
            .append(Html.bold(overview.project().value()))
            .append(" — tasklar\n")
            .append(repoLink(overview.repo()))
            .append("\n\n");
    if (overview.total() == 0) {
      html.append("Hali task yo'q — birinchisini yarating.");
    } else {
      overview
          .counts()
          .forEach(
              (status, count) ->
                  html.append(label(status)).append(" — ").append(count).append('\n'));
      html.append("\n<i>Guruhni tanlang.</i>");
    }
    List<List<Button>> rows = new ArrayList<>();
    List<Button> groups =
        overview.counts().entrySet().stream()
            .map(
                group ->
                    new Button(
                        label(group.getKey()) + " (" + group.getValue() + ")",
                        Actions.taskGroup(group.getKey(), 0)))
            .toList();
    for (int i = 0; i < groups.size(); i += GROUPS_PER_ROW) {
      rows.add(groups.subList(i, Math.min(i + GROUPS_PER_ROW, groups.size())));
    }
    rows.add(List.of(new Button("➕ Yangi task", Actions.TASK_NEW)));
    rows.add(List.of(ConnectionScreens.backToCard(overview.project())));
    return new Screen(html.toString().strip(), rows);
  }

  static Screen page(TasksResult.Page page) {
    String title =
        label(page.status())
            + " · "
            + Html.bold(page.project().value())
            + (page.page() > 0 ? " · sahifa " + (page.page() + 1) : "");
    String body = page.tasks().isEmpty() ? "Bu guruhda task yo'q." : "Bosilsa — tafsilot.";
    List<List<Button>> rows = new ArrayList<>();
    for (Task task : page.tasks()) {
      rows.add(List.of(new Button(listLabel(task), Actions.openTask(task.iid()))));
    }
    List<Button> navigation = new ArrayList<>();
    if (page.page() > 0) {
      navigation.add(new Button("◀️", Actions.taskGroup(page.status(), page.page() - 1)));
    }
    if (page.hasNext()) {
      navigation.add(new Button("▶️", Actions.taskGroup(page.status(), page.page() + 1)));
    }
    if (!navigation.isEmpty()) {
      rows.add(navigation);
    }
    rows.add(List.of(new Button("⬅️ Tasklar", Actions.TASKS)));
    return new Screen(title + "\n\n" + body, rows);
  }

  static Screen opened(TasksResult.Opened opened) {
    Task task = opened.task();
    StringBuilder html =
        new StringBuilder(icon(opened.status()))
            .append(" <b>#")
            .append(task.iid())
            .append(" ")
            .append(Html.escape(task.title()))
            .append("</b>\n")
            .append(label(opened.status()));
    task.dueDate().ifPresent(due -> html.append(" · 📅 ").append(DUE.format(due)));
    html.append("\n").append(link(task.webUrl(), "🔗 Ochish"));
    if (!task.description().isBlank()) {
      html.append("\n\n").append(Html.escape(preview(task.description())));
    }
    if (!opened.mergeRequests().isEmpty()) {
      html.append("\n\n<b>Merge request'lar</b>");
      opened.mergeRequests().forEach(mr -> html.append("\n").append(mergeRequest(mr)));
    }
    Button toggle =
        task.open()
            ? new Button("✔️ Yopish", Actions.TASK_CLOSE + task.iid())
            : new Button("↩️ Qayta ochish", Actions.TASK_REOPEN + task.iid());
    return new Screen(
        html.toString(),
        List.of(
            List.of(toggle),
            List.of(
                new Button("⬅️ " + label(opened.status()), Actions.taskGroup(opened.status(), 0)),
                new Button("✅ Tasklar", Actions.TASKS))));
  }

  static Screen created(TasksResult.Created created) {
    Task task = created.task();
    return new Screen(
        "✅ Task yaratildi: <b>#"
            + task.iid()
            + " "
            + Html.escape(task.title())
            + "</b>\n📁 "
            + Html.escape(created.project().value())
            + " · 🏷 "
            + ManageTasksUseCase.AI_TASK_LABEL
            + "\n"
            + link(task.webUrl(), "🔗 Ochish"),
        List.of(
            List.of(new Button("📋 Ochish", Actions.openTask(task.iid()))),
            List.of(new Button("✅ Tasklar", Actions.TASKS))));
  }

  static Screen askTitle(ProjectName project) {
    return new Screen(
        "✍️ "
            + Html.bold(project.value())
            + " uchun yangi task sarlavhasini yozing.\n<i>Masalan: Login sahifasida parolni"
            + " tiklash</i>",
        List.of(List.of(BotScreens.CANCEL)));
  }

  static Screen askDescription() {
    return new Screen(
        "✍️ Tavsifni yozing: nima qilish kerak, qanday bo'lsa tayyor hisoblanadi.",
        List.of(
            List.of(new Button("⏭ Tavsifsiz", Actions.TASK_SKIP_DESCRIPTION)),
            List.of(BotScreens.CANCEL)));
  }

  /**
   * @param current hozirgi qiymat — foydalanuvchi nusxalab tahrirlashi uchun
   */
  static Screen askEdit(String what, String current) {
    String shown = current.isBlank() ? "<i>bo'sh</i>" : Html.code(preview(current));
    return new Screen(
        "✏️ Yangi " + what + "ni yozing.\n\nHozirgi:\n" + shown,
        List.of(List.of(new Button("⬅️ Orqaga", Actions.TASK_REVIEW))));
  }

  /** Tasdiqsiz hech narsa yaratilmaydi. */
  static Screen confirm(NewTask draft, ProjectName project, Repo repo) {
    String description =
        draft.description().isBlank()
            ? "<i>tavsifsiz</i>"
            : Html.escape(preview(draft.description()));
    String html =
        "📝 <b>Yangi task</b>\n📁 "
            + Html.escape(project.value())
            + " → 📂 "
            + Html.escape(repo.path())
            + " · 🏷 "
            + ManageTasksUseCase.AI_TASK_LABEL
            + "\n\n<b>"
            + Html.escape(draft.title())
            + "</b>\n\n"
            + description
            + "\n\n<i>Yaratilsinmi?</i>";
    return new Screen(
        html,
        List.of(
            List.of(new Button("✅ Yaratish", Actions.TASK_CONFIRM)),
            List.of(
                new Button("✏️ Sarlavha", Actions.TASK_EDIT_TITLE),
                new Button("✏️ Tavsif", Actions.TASK_EDIT_DESCRIPTION)),
            List.of(new Button("✖️ Bekor qilish", Actions.TASK_DISCARD))));
  }

  static Screen discarded() {
    return new Screen(
        "✖️ Task yaratilmadi.",
        List.of(List.of(new Button("✅ Tasklar", Actions.TASKS)), List.of(BotScreens.BACK_HOME)));
  }

  /** Tasdiqlash ekrani eskirgan (bot qayta ishga tushgan yoki task allaqachon yaratilgan). */
  static Screen noDraft() {
    return new Screen(
        "ℹ️ Qoralama topilmadi — task allaqachon yaratilgan yoki bekor qilingan.",
        List.of(
            List.of(new Button("➕ Yangi task", Actions.TASK_NEW)),
            List.of(new Button("✅ Tasklar", Actions.TASKS))));
  }

  private static String listLabel(Task task) {
    String title = task.title().replaceAll("\\s+", " ");
    String preview =
        title.length() <= TITLE_PREVIEW ? title : title.substring(0, TITLE_PREVIEW).strip() + "…";
    String due = task.dueDate().map(date -> " · 📅 " + DUE.format(date)).orElse("");
    return "#" + task.iid() + " · " + preview + due;
  }

  private static String mergeRequest(MergeRequest mr) {
    String state =
        switch (mr.state()) {
          case OPENED -> "🔀 ochiq";
          case MERGED -> "✅ merge bo'lgan";
          case CLOSED -> "⚪ yopilgan";
        };
    return state + " · " + link(mr.webUrl(), "!" + mr.iid() + " " + mr.title());
  }

  private static String repoLink(Repo repo) {
    return "📂 " + link(repo.webUrl(), repo.path());
  }

  private static String link(URI url, String text) {
    return "<a href=\"" + Html.escape(url.toString()) + "\">" + Html.escape(text) + "</a>";
  }

  private static String preview(String text) {
    return text.length() <= DESCRIPTION_PREVIEW
        ? text
        : text.substring(0, DESCRIPTION_PREVIEW).strip() + "…";
  }
}
