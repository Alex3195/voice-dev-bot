package com.alex.voicedevbot.adapter.in.cli;

import com.alex.voicedevbot.application.port.in.SttBenchmarkReport;
import com.alex.voicedevbot.application.port.in.SttBenchmarkReport.EngineSummary;
import com.alex.voicedevbot.application.port.in.SttBenchmarkReport.Outcome;
import com.alex.voicedevbot.application.port.in.SttBenchmarkReport.Sample;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.WordErrorRate.WordError;
import com.alex.voicedevbot.domain.WordErrorRate.WordError.Kind;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** STT o'lchovi hisobotini Markdown'ga aylantiradi. */
final class SttBenchmarkMarkdown {

  /** Har dvigatel uchun nechta eng ko'p xato ko'rsatiladi. */
  static final int TOP_ERRORS = 15;

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final String MISSING = "—";

  private SttBenchmarkMarkdown() {}

  static String render(SttBenchmarkReport report, ZonedDateTime generatedAt) {
    StringBuilder md =
        new StringBuilder("# STT o'lchovi — ").append(TIME.format(generatedAt)).append("\n\n");
    if (report.samples().isEmpty()) {
      return md.append(
              "Tasdiqlangan transkript yo'q. Botda `✅ Task yaratish` → `✅ Yaratish` bilan task"
                  + " yarating — har biri bitta namuna.\n")
          .toString();
    }
    Duration audio =
        report.samples().stream().map(Sample::audio).reduce(Duration.ZERO, Duration::plus);
    int words = report.samples().stream().mapToInt(Sample::referenceWords).sum();
    md.append("Tasdiqlangan audio: ")
        .append(report.samples().size())
        .append(" ta · ")
        .append(minutes(audio))
        .append(" · ")
        .append(words)
        .append(" so'z\n\nTo'g'ri javob — Claude tuzatgan va task bilan tasdiqlangan matn")
        .append(" (`confirmed_text`). Matnlar kichik harf, tinish belgilarsiz solishtiriladi.\n")
        .append(
            "\nJurnal ustuni — o'sha paytdagi prompt (o'sha paytdagi lug'at) bilan; qayta o'tkazilgan"
                + " dvigatellar — bir xil, hozirgi lug'at bilan. Tasdiqlangan matn jurnal matnidan"
                + " tuzatilgan, shuning uchun jurnal ustuni biroz yutadi.\n");
    summary(md, report);
    topErrors(md, report);
    samples(md, report);
    failures(md, report);
    texts(md, report);
    return md.toString();
  }

  private static void summary(StringBuilder md, SttBenchmarkReport report) {
    md.append("\n## Umumiy\n\n")
        .append("| Dvigatel | WER | Almashtirilgan | Tushib qolgan | Ortiqcha | Audio | Tezlik |")
        .append(" Xato |\n|---|---|---|---|---|---|---|---|\n");
    for (EngineSummary summary : report.summaries()) {
      md.append("| ")
          .append(cell(summary.engine()))
          .append(" | ")
          .append(summary.transcribed() == 0 ? MISSING : percent(summary.wer().rate()))
          .append(" | ")
          .append(summary.wer().count(Kind.SUBSTITUTION))
          .append(" | ")
          .append(summary.wer().count(Kind.DELETION))
          .append(" | ")
          .append(summary.wer().count(Kind.INSERTION))
          .append(" | ")
          .append(summary.transcribed())
          .append(" ta · ")
          .append(minutes(summary.audio()))
          .append(" | ")
          .append(
              summary
                  .realTimeFactor()
                  .map(factor -> String.format(Locale.ROOT, "%.2f× real vaqt", factor))
                  .orElse(MISSING))
          .append(" | ")
          .append(summary.failed())
          .append(" |\n");
    }
    md.append(
        "\nWER har dvigatel matn bergan audiolar bo'yicha. Tezlik — audio'ning bir soniyasiga"
            + " necha soniya ishlov (1 dan kichik — real vaqtdan tez).\n");
  }

  private static void topErrors(StringBuilder md, SttBenchmarkReport report) {
    md.append("\n## Eng ko'p xatolar\n");
    for (String engine : report.engines()) {
      List<Map.Entry<WordError, Long>> top = report.topErrors(engine, TOP_ERRORS);
      md.append("\n### ").append(engine).append("\n\n");
      if (top.isEmpty()) {
        md.append("Xato yo'q.\n");
        continue;
      }
      md.append("| To'g'ri | Eshitilgan | Marta |\n|---|---|---|\n");
      for (Map.Entry<WordError, Long> error : top) {
        md.append("| ")
            .append(word(error.getKey().expected()))
            .append(" | ")
            .append(word(error.getKey().heard()))
            .append(" | ")
            .append(error.getValue())
            .append(" |\n");
      }
    }
  }

  private static void samples(StringBuilder md, SttBenchmarkReport report) {
    md.append("\n## Har bir audio\n\n| # | Project | Audio | So'z |");
    report.engines().forEach(engine -> md.append(' ').append(cell(engine)).append(" |"));
    md.append("\n|---|---|---|---|");
    report.engines().forEach(engine -> md.append("---|"));
    md.append('\n');
    for (Sample sample : report.samples()) {
      md.append("| ")
          .append(sample.journalId())
          .append(" | ")
          .append(
              sample
                  .project()
                  .map(ProjectName::value)
                  .map(SttBenchmarkMarkdown::cell)
                  .orElse(MISSING))
          .append(" | ")
          .append(clock(sample.audio()))
          .append(" | ")
          .append(sample.referenceWords())
          .append(" |");
      for (String engine : report.engines()) {
        md.append(' ').append(outcome(sample.outcomes().get(engine))).append(" |");
      }
      md.append('\n');
    }
  }

  /** Raqam yetmaydi: takrorlanib qolish (hallucination) kabi xatolar faqat matnda ko'rinadi. */
  private static void texts(StringBuilder md, SttBenchmarkReport report) {
    md.append("\n## Matnlar\n");
    for (Sample sample : report.samples()) {
      md.append("\n<details><summary>#")
          .append(sample.journalId())
          .append(sample.project().map(name -> " · " + name.value()).orElse(""))
          .append("</summary>\n\n**To'g'ri:** ")
          .append(paragraph(sample.reference()))
          .append('\n');
      for (String engine : report.engines()) {
        md.append("\n**").append(engine).append("**");
        switch (sample.outcomes().get(engine)) {
          case Outcome.Transcribed done ->
              md.append(" (")
                  .append(percent(done.wer().rate()))
                  .append("): ")
                  .append(paragraph(done.text()));
          case Outcome.Failed(var reason) ->
              md.append(": _xato — ").append(paragraph(reason)).append('_');
          case null -> md.append(": ").append(MISSING);
        }
        md.append('\n');
      }
      md.append("\n</details>\n");
    }
  }

  private static String paragraph(String text) {
    return text.strip().replaceAll("\\s+", " ").replace("<", "&lt;");
  }

  private static void failures(StringBuilder md, SttBenchmarkReport report) {
    StringBuilder list = new StringBuilder();
    for (Sample sample : report.samples()) {
      for (String engine : report.engines()) {
        if (sample.outcomes().get(engine) instanceof Outcome.Failed(var reason)) {
          list.append("- #")
              .append(sample.journalId())
              .append(" · ")
              .append(engine)
              .append(": ")
              .append(reason.replace('\n', ' '))
              .append('\n');
        }
      }
    }
    if (!list.isEmpty()) {
      md.append("\n## Xatolar\n\n").append(list);
    }
  }

  private static String outcome(Outcome outcome) {
    return switch (outcome) {
      case Outcome.Transcribed done -> percent(done.wer().rate());
      case Outcome.Failed ignored -> "xato";
      case null -> MISSING;
    };
  }

  private static String percent(double rate) {
    return String.format(Locale.ROOT, "%.1f%%", rate * 100);
  }

  private static String minutes(Duration duration) {
    return String.format(Locale.ROOT, "%.1f daqiqa", duration.toMillis() / 60_000.0);
  }

  private static String clock(Duration duration) {
    return String.format(Locale.ROOT, "%d:%02d", duration.toMinutes(), duration.toSecondsPart());
  }

  private static String word(String word) {
    return word.isEmpty() ? MISSING : cell(word);
  }

  /** Jadval katagini buzmasin. */
  private static String cell(String text) {
    return text.replace("|", "\\|").replace('\n', ' ');
  }
}
