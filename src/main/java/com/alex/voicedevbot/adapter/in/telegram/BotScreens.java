package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.adapter.in.telegram.Screen.Attachment;
import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.port.in.ProjectCommandResult.ProjectSummary;
import com.alex.voicedevbot.application.port.in.TranscriptsResult;
import com.alex.voicedevbot.domain.LoggedTranscript;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TranscriptRecord;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Use-case natijalaridan ekranlar (matn + tugmalar) yasaydi. Holatsiz, faqat ko'rinish. */
final class BotScreens {

  /**
   * Tanlash tugmalarida ko'rsatiladigan tillar; boshqasini {@code /lang <kod>} bilan qo'yish
   * mumkin.
   */
  static final Map<String, String> LANGUAGES = languages();

  static final Button BACK_HOME = new Button("⬅️ Orqaga", Actions.HOME);
  static final Button CANCEL = new Button("✖️ Bekor qilish", Actions.CANCEL);

  private static final int TERMS_PER_ROW = 2;
  private static final int MAX_TERM_BUTTONS = 90;
  private static final int PREVIEW_LENGTH = 40;
  private static final DateTimeFormatter LIST_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");
  private static final DateTimeFormatter FULL_TIME =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  private BotScreens() {}

  static Screen home(Optional<ProjectName> activeProject, SpeechLanguage language) {
    String project =
        activeProject.map(name -> Html.bold(name.value())).orElse("<i>tanlanmagan</i>");
    String html =
        "🎙 <b>Voice Dev Bot</b>\n"
            + "Ovoz, audio fayl yoki video yuboring — matnga aylantiraman.\n\n"
            + "📁 Faol project: "
            + project
            + "\n🌐 Nutq tili: "
            + languageLabel(language);
    return new Screen(
        html,
        List.of(
            List.of(
                new Button("📁 Projectlar", Actions.PROJECTS),
                new Button("📖 Lug'at", Actions.GLOSSARY)),
            List.of(
                new Button("⚙️ Sozlamalar", Actions.SETTINGS),
                new Button("❓ Yordam", Actions.HELP))));
  }

  static Screen settings(SpeechLanguage language) {
    return new Screen(
        "⚙️ <b>Sozlamalar</b>\n\n🌐 Nutq tili: " + languageLabel(language),
        List.of(
            List.of(new Button("🌐 Nutq tili", Actions.LANGUAGES)),
            List.of(new Button("🔗 GitLab", Actions.GITLAB)),
            List.of(new Button("📝 Projectsiz transkriptlar", Actions.transcripts(null, 0))),
            List.of(BACK_HOME)));
  }

  /**
   * Project tugmasi bosilganda: project faol qilinadi va uning bo'limlari ko'rsatiladi.
   *
   * @param repo ulangan repo yoki "Repo ulash" tugmasi
   */
  static Screen projectCard(ProjectSummary project, Optional<Button> repo) {
    String key = project.name().key();
    String html =
        "📁 "
            + Html.bold(project.name().value())
            + " · ✅ faol\n📖 Lug'atda "
            + project.termCount()
            + " atama\n\nOvoz yuborsangiz, shu projectga yoziladi.";
    List<List<Button>> rows = new ArrayList<>();
    rows.add(
        List.of(
            new Button("📝 Transkriptlar", Actions.transcripts(key, 0)),
            new Button("📖 Lug'at", Actions.GLOSSARY)));
    rows.add(
        List.of(
            new Button("✅ Tasklar", Actions.soon(key)),
            new Button("📄 Hujjatlar", Actions.soon(key))));
    repo.ifPresent(button -> rows.add(List.of(button)));
    rows.add(List.of(new Button("⬅️ Projectlar", Actions.PROJECTS)));
    return new Screen(html, rows);
  }

  /** Yangi project yaratilganda — darhol repo ulashni taklif qiladi. */
  static Screen withRepoOffer(Screen screen) {
    List<List<Button>> rows = new ArrayList<>(screen.rows());
    rows.add(rows.size() - 1, List.of(new Button("🔗 Repo ulash", Actions.REPO)));
    return new Screen(screen.html(), rows, screen.attachments());
  }

  /**
   * @param project {@code null} — projectsiz transkriptlar
   */
  static Screen transcripts(TranscriptsResult.Page page, ProjectName project, ZoneId zone) {
    String key = project == null ? null : project.key();
    String title =
        project == null
            ? "📝 <b>Projectsiz transkriptlar</b>"
            : "📝 " + Html.bold(project.value()) + " — transkriptlar";
    String body =
        page.items().isEmpty()
            ? "Hali transkript yo'q. Ovoz yuboring — shu yerda saqlanadi."
            : "Eng yangisi tepada. Bosilsa — matn va audio.";
    List<List<Button>> rows = new ArrayList<>();
    for (LoggedTranscript item : page.items()) {
      rows.add(
          List.of(new Button(listLabel(item.record(), zone), Actions.openTranscript(item.id()))));
    }
    List<Button> navigation = new ArrayList<>();
    if (page.page() > 0) {
      navigation.add(new Button("◀️", Actions.transcripts(key, page.page() - 1)));
    }
    if (page.hasNext()) {
      navigation.add(new Button("▶️", Actions.transcripts(key, page.page() + 1)));
    }
    if (!navigation.isEmpty()) {
      rows.add(navigation);
    }
    String back = project == null ? Actions.SETTINGS : Actions.selectProject(key);
    rows.add(List.of(new Button("⬅️ Orqaga", back)));
    String pageNote = page.page() > 0 ? " · sahifa " + (page.page() + 1) : "";
    return new Screen(title + pageNote + "\n\n" + body, rows);
  }

  static Screen transcriptEntry(LoggedTranscript entry, ZoneId zone) {
    TranscriptRecord record = entry.record();
    String project = record.project().map(name -> Html.escape(name.value())).orElse("projectsiz");
    String html =
        "📝 <b>Transkript #"
            + entry.id()
            + "</b>\n<i>🕒 "
            + FULL_TIME.format(record.createdAt().atZone(zone))
            + durationSuffix(record.audio().duration())
            + "\n📁 "
            + project
            + " · 🌐 "
            + record.language().code()
            + "\n🤖 "
            + Html.escape(record.transcription().model())
            + "</i>\n\n"
            + Html.escape(record.transcription().transcript().text());
    String key = record.project().map(ProjectName::key).orElse(null);
    return new Screen(
        html,
        List.of(List.of(new Button("⬅️ Ro'yxatga", Actions.transcripts(key, 0)))),
        List.of(new Attachment(record.audio().kind(), record.audio().ref().id())));
  }

  static Screen projects(List<ProjectSummary> summaries) {
    List<List<Button>> rows = new ArrayList<>();
    for (ProjectSummary summary : summaries) {
      String mark = summary.active() ? "✅ " : "";
      String label = mark + summary.name().value() + " · " + summary.termCount() + " atama";
      rows.add(List.of(new Button(label, Actions.selectProject(summary.name().key()))));
    }
    rows.add(List.of(new Button("➕ Yangi project", Actions.NEW_PROJECT)));
    rows.add(List.of(BACK_HOME));
    String html =
        summaries.isEmpty()
            ? "📁 <b>Projectlar</b>\n\nHali project yo'q — birinchisini qo'shing."
            : "📁 <b>Projectlar</b>\n\nFaol projectni tanlang. Uning lug'ati ovozni matnga"
                + " aylantirishda ishlatiladi.";
    return new Screen(html, rows);
  }

  static Screen glossary(ProjectName project, List<String> terms) {
    String body =
        terms.isEmpty()
            ? "Lug'at bo'sh. Project atamalarini qo'shing — masalan, nomlar, mahsulotlar, texnik"
                + " so'zlar. Bot ularni to'g'ri yozadi."
            : terms.stream().map(Html::code).collect(Collectors.joining(" · "));
    String html =
        "📖 " + Html.bold(project.value()) + " lug'ati — " + terms.size() + " atama\n\n" + body;
    List<Button> actions = new ArrayList<>(List.of(new Button("➕ Qo'shish", Actions.ADD_TERMS)));
    if (!terms.isEmpty()) {
      actions.add(new Button("➖ O'chirish", Actions.REMOVE_MODE));
    }
    return new Screen(html, List.of(actions, List.of(BACK_HOME)));
  }

  static Screen removeTerms(ProjectName project, List<String> terms) {
    List<List<Button>> rows = new ArrayList<>();
    List<String> shown = terms.subList(0, Math.min(terms.size(), MAX_TERM_BUTTONS));
    for (int i = 0; i < shown.size(); i += TERMS_PER_ROW) {
      rows.add(
          shown.subList(i, Math.min(i + TERMS_PER_ROW, shown.size())).stream()
              .map(term -> new Button("❌ " + term, Actions.removeTerm(term)))
              .toList());
    }
    rows.add(List.of(new Button("✔️ Tayyor", Actions.GLOSSARY)));
    String html =
        terms.isEmpty()
            ? "📖 " + Html.bold(project.value()) + " lug'ati bo'sh."
            : "➖ " + Html.bold(project.value()) + " — o'chirish uchun atamani bosing.";
    return new Screen(html, rows);
  }

  static Screen languages(SpeechLanguage current) {
    List<List<Button>> rows = new ArrayList<>();
    LANGUAGES.forEach(
        (code, label) ->
            rows.add(
                List.of(
                    new Button(
                        (code.equals(current.code()) ? "✅ " : "") + label,
                        Actions.setLanguage(code)))));
    rows.add(List.of(new Button("⬅️ Orqaga", Actions.SETTINGS)));
    return new Screen(
        "🌐 <b>Nutq tili</b>\n\nQaysi tilda gapirasiz? Bot ovozni shu til bo'yicha taniydi.\n"
            + "<i>Qoraqalpoqcha uchun eng yaqini — qozoq.</i>",
        rows);
  }

  static Screen help() {
    return new Screen(
        """
        ❓ <b>Qanday ishlatiladi</b>

        1️⃣ <b>Project tanlang</b> — 📁 Projectlar. Yangi bo'lsa ➕ bilan qo'shing.
        2️⃣ <b>Lug'atga atamalar qo'shing</b> — 📖 Lug'at → ➕. Nomlar va texnik so'zlar \
        (masalan: <code>ELT imzo</code>, <code>PVX</code>) shunda to'g'ri yoziladi.
        3️⃣ <b>Ovoz yuboring</b> — voice, audio fayl, video yoki dumaloq video (20 MB gacha).

        📝 Avvalgi transkriptlar — project kartochkasida (📁 Projectlar → project).
        🌐 Boshqa tilda gapirsangiz — ⚙️ Sozlamalar → 🌐 Nutq tili.

        <b>Buyruqlar</b> (xohlasangiz):
        /start — bosh menyu
        /project &lt;nom&gt; — projectni tanlash
        /glossary add &lt;atama&gt;, &lt;atama&gt; — atama qo'shish
        /lang &lt;kod&gt; — til (uz, kk, ru...)""",
        List.of(List.of(BACK_HOME)));
  }

  static Screen askProjectName() {
    return new Screen(
        "✍️ Yangi project nomini yozing.\n<i>Masalan: ELT imzo</i>", List.of(List.of(CANCEL)));
  }

  static Screen askTerms(ProjectName project) {
    return new Screen(
        "✍️ "
            + Html.bold(project.value())
            + " uchun atamalarni vergul bilan yozing.\n<i>Masalan: kassa bo'limi, Klaes, PVX</i>",
        List.of(List.of(CANCEL)));
  }

  static Screen noActiveProject() {
    return new Screen(
        "📖 Lug'at faol projectga tegishli.\nAvval projectni tanlang yoki qo'shing.",
        List.of(List.of(new Button("📁 Projectlar", Actions.PROJECTS)), List.of(BACK_HOME)));
  }

  static Screen transcript(
      String text, Optional<ProjectName> activeProject, SpeechLanguage language) {
    String project =
        activeProject.map(name -> Html.escape(name.value())).orElse("project tanlanmagan");
    String html =
        "📝 <b>Matn</b>\n\n"
            + Html.escape(text)
            + "\n\n<i>📁 "
            + project
            + " · 🌐 "
            + language.code()
            + "</i>";
    String switchLabel =
        activeProject.isPresent() ? "📁 Projectni almashtirish" : "📁 Project tanlash";
    return new Screen(
        html, List.of(List.of(new Button(switchLabel, Actions.NEW_MESSAGE + Actions.PROJECTS))));
  }

  private static String listLabel(TranscriptRecord record, ZoneId zone) {
    String text = record.transcription().transcript().text().replaceAll("\\s+", " ");
    String preview =
        text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH).strip() + "…";
    return LIST_TIME.format(record.createdAt().atZone(zone))
        + durationSuffix(record.audio().duration())
        + " · "
        + preview;
  }

  /** Davomiylik noma'lum bo'lsa (hujjat) ko'rsatilmaydi. */
  private static String durationSuffix(Duration duration) {
    if (duration.isZero()) {
      return "";
    }
    return " · %d:%02d".formatted(duration.toMinutes(), duration.toSecondsPart());
  }

  static String languageLabel(SpeechLanguage language) {
    return LANGUAGES.getOrDefault(language.code(), Html.code(language.code()));
  }

  private static Map<String, String> languages() {
    Map<String, String> languages = new LinkedHashMap<>();
    languages.put("uz", "🇺🇿 O'zbek");
    languages.put("kk", "🇰🇿 Qozoq / Qoraqalpoq");
    languages.put("ru", "🇷🇺 Rus");
    languages.put("en", "🇬🇧 Ingliz");
    languages.put("tr", "🇹🇷 Turk");
    return Collections.unmodifiableMap(languages);
  }
}
