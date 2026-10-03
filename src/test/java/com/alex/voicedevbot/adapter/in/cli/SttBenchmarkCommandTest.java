package com.alex.voicedevbot.adapter.in.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.application.port.in.SttBenchmarkReport;
import com.alex.voicedevbot.application.port.in.SttBenchmarkReport.Outcome;
import com.alex.voicedevbot.application.port.in.SttBenchmarkReport.Sample;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.WordErrorRate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SttBenchmarkCommandTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-03T11:28:12Z"), ZoneOffset.UTC);
  private static final String CORRECT = "QR kod orqali to'lov qilish kerak";

  @TempDir Path dir;

  private static Outcome.Transcribed transcribed(String text, Duration took) {
    return new Outcome.Transcribed(
        text, WordErrorRate.of(CORRECT, text), Optional.ofNullable(took));
  }

  private static SttBenchmarkReport report() {
    return new SttBenchmarkReport(
        List.of(SttBenchmarkReport.JOURNAL, "whisper | q5"),
        List.of(
            new Sample(
                15,
                Optional.of(new ProjectName("utility bills")),
                Duration.ofSeconds(91),
                CORRECT,
                6,
                Map.of(
                    SttBenchmarkReport.JOURNAL,
                    transcribed("kar kod orqali tolov qilish kerak", null),
                    "whisper | q5",
                    transcribed("QR kod orqali <to'lov> qilish kerak", Duration.ofSeconds(23)))),
            new Sample(
                16,
                Optional.empty(),
                Duration.ofSeconds(5),
                "salom",
                1,
                Map.of(
                    SttBenchmarkReport.JOURNAL,
                    new Outcome.Transcribed(
                        "salom", WordErrorRate.of("salom", "salom"), Optional.empty()),
                    "whisper | q5",
                    new Outcome.Failed("whisper-server\ndown")))));
  }

  @Test
  void should_render_summary_errors_samples_failures_and_texts() {
    String md = SttBenchmarkMarkdown.render(report(), ZonedDateTime.now(CLOCK));

    assertThat(md)
        .startsWith("# STT o'lchovi — 2026-10-03 11:28\n")
        .contains("Tasdiqlangan audio: 2 ta · 1.6 daqiqa · 7 so'z")
        .contains("| jurnal (o'sha paytdagi) | 28.6% | 2 | 0 | 0 | 2 ta · 1.6 daqiqa | — | 0 |")
        .contains("| whisper \\| q5 | 0.0% | 0 | 0 | 0 | 1 ta · 1.5 daqiqa | 0.25× real vaqt | 1 |")
        .contains("| qr | kar | 1 |")
        .contains("### whisper | q5\n\nXato yo'q.")
        .contains("| 15 | utility bills | 1:31 | 6 | 33.3% | 0.0% |")
        .contains("| 16 | — | 0:05 | 1 | 0.0% | xato |")
        .contains("- #16 · whisper | q5: whisper-server down")
        .contains("<details><summary>#15 · utility bills</summary>")
        .contains("**To'g'ri:** QR kod orqali to'lov qilish kerak")
        .contains("**whisper | q5** (0.0%): QR kod orqali &lt;to'lov> qilish kerak")
        .contains("**whisper | q5**: _xato — whisper-server down_");
  }

  @Test
  void should_explain_how_to_collect_samples_when_none_are_confirmed() {
    String md =
        SttBenchmarkMarkdown.render(
            new SttBenchmarkReport(List.of(SttBenchmarkReport.JOURNAL), List.of()),
            ZonedDateTime.now(CLOCK));

    assertThat(md).contains("Tasdiqlangan transkript yo'q").doesNotContain("## Umumiy");
  }

  @Test
  void should_write_report_into_timestamped_file() throws IOException {
    Path reportDir = dir.resolve("reports");

    Path report = new SttBenchmarkCommand(SttBenchmarkCommandTest::report, reportDir, CLOCK).run();

    assertThat(report).isEqualTo(reportDir.resolve("2026-10-03T11-28-12.md"));
    assertThat(Files.readString(report)).startsWith("# STT o'lchovi");
  }

  @Test
  void should_fail_clearly_when_report_cannot_be_written() throws IOException {
    Path notADirectory = Files.createFile(dir.resolve("file"));

    assertThatThrownBy(
            () ->
                new SttBenchmarkCommand(SttBenchmarkCommandTest::report, notADirectory, CLOCK)
                    .run())
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("STT benchmark report");
  }
}
