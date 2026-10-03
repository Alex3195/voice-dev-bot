package com.alex.voicedevbot.adapter.in.cli;

import com.alex.voicedevbot.application.port.in.BenchmarkSpeechToTextUseCase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * {@code ./gradlew sttBenchmark}: o'lchaydi va Markdown hisobotni {@code <papka>/<vaqt>.md} ga
 * yozadi. Hisobotda transkript so'zlari bor — papka repo'ga commit qilinmaydi ({@code build/}).
 */
public class SttBenchmarkCommand {

  private static final DateTimeFormatter FILE_NAME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'.md'");

  private final BenchmarkSpeechToTextUseCase benchmark;
  private final Path reportDir;
  private final Clock clock;

  public SttBenchmarkCommand(BenchmarkSpeechToTextUseCase benchmark, Path reportDir, Clock clock) {
    this.benchmark = Objects.requireNonNull(benchmark, "benchmark");
    this.reportDir = Objects.requireNonNull(reportDir, "reportDir");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * @return yozilgan hisobot fayli
   */
  public Path run() {
    String markdown = SttBenchmarkMarkdown.render(benchmark.run(), ZonedDateTime.now(clock));
    Path report = reportDir.resolve(FILE_NAME.format(ZonedDateTime.now(clock)));
    try {
      Files.createDirectories(reportDir);
      Files.writeString(report, markdown, StandardCharsets.UTF_8);
      return report;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write STT benchmark report " + report, e);
    }
  }
}
