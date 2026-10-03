package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.WordErrorRate;
import com.alex.voicedevbot.domain.WordErrorRate.WordError;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * STT dvigatellarini tasdiqlangan transkriptlar bo'yicha solishtirish natijasi.
 *
 * @param engines dvigatellar nomi, hisobotdagi tartibda; birinchisi — jurnaldagi (o'sha paytda bot
 *     ishlatgan) matn
 * @param samples har bir tasdiqlangan audio
 */
public record SttBenchmarkReport(List<String> engines, List<Sample> samples) {

  /** Jurnaldagi matn: o'sha paytda bot ishlatgan Whisper (qayta ishlanmaydi, bepul). */
  public static final String JOURNAL = "jurnal (o'sha paytdagi)";

  public SttBenchmarkReport {
    engines = List.copyOf(engines);
    samples = List.copyOf(samples);
  }

  /**
   * @param audio audio davomiyligi
   * @param reference tasdiqlangan matn ("to'g'ri javob")
   * @param referenceWords undagi so'zlar soni
   * @param outcomes dvigatel nomi → natija
   */
  public record Sample(
      long journalId,
      Optional<ProjectName> project,
      Duration audio,
      String reference,
      int referenceWords,
      Map<String, Outcome> outcomes) {

    public Sample {
      Objects.requireNonNull(project, "project");
      Objects.requireNonNull(audio, "audio");
      Objects.requireNonNull(reference, "reference");
      outcomes = Map.copyOf(outcomes);
    }
  }

  public sealed interface Outcome {

    /**
     * @param took ishlov vaqti; jurnaldagi matn uchun — bo'sh
     */
    record Transcribed(String text, WordErrorRate wer, Optional<Duration> took) implements Outcome {

      public Transcribed {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(wer, "wer");
        Objects.requireNonNull(took, "took");
      }
    }

    record Failed(String reason) implements Outcome {

      public Failed {
        Objects.requireNonNull(reason, "reason");
      }
    }
  }

  /**
   * Bitta dvigatelning umumiy natijasi; WER faqat u matn bergan audiolar bo'yicha.
   *
   * @param audio u matn bergan audiolar davomiyligi
   * @param took ishlov vaqti; jurnaldagi matn uchun — bo'sh
   */
  public record EngineSummary(
      String engine,
      WordErrorRate wer,
      int transcribed,
      int failed,
      Duration audio,
      Optional<Duration> took) {

    /** Audio'ning bir soniyasiga necha soniya ishlov (1 dan kichik — real vaqtdan tez). */
    public Optional<Double> realTimeFactor() {
      return took.filter(ignored -> !audio.isZero())
          .map(time -> time.toMillis() / (double) audio.toMillis());
    }
  }

  public List<EngineSummary> summaries() {
    return engines.stream().map(this::summary).toList();
  }

  /** Eng ko'p takrorlangan xatolar — lug'atga nima qo'shish kerakligini ko'rsatadi. */
  public List<Map.Entry<WordError, Long>> topErrors(String engine, int limit) {
    Map<WordError, Long> counts =
        transcribed(engine)
            .flatMap(outcome -> outcome.wer().errors().stream())
            .collect(
                Collectors.groupingBy(
                    Function.identity(), LinkedHashMap::new, Collectors.counting()));
    return counts.entrySet().stream()
        .sorted(Map.Entry.<WordError, Long>comparingByValue(Comparator.reverseOrder()))
        .limit(limit)
        .toList();
  }

  private EngineSummary summary(String engine) {
    WordErrorRate wer = WordErrorRate.none();
    int transcribed = 0;
    int failed = 0;
    Duration audio = Duration.ZERO;
    Duration took = Duration.ZERO;
    boolean timed = false;
    for (Sample sample : samples) {
      switch (sample.outcomes().get(engine)) {
        case Outcome.Transcribed done -> {
          wer = wer.plus(done.wer());
          transcribed++;
          audio = audio.plus(sample.audio());
          if (done.took().isPresent()) {
            took = took.plus(done.took().get());
            timed = true;
          }
        }
        case Outcome.Failed ignored -> failed++;
        case null -> failed++;
      }
    }
    return new EngineSummary(
        engine, wer, transcribed, failed, audio, timed ? Optional.of(took) : Optional.empty());
  }

  private java.util.stream.Stream<Outcome.Transcribed> transcribed(String engine) {
    return samples.stream()
        .map(sample -> sample.outcomes().get(engine))
        .filter(Outcome.Transcribed.class::isInstance)
        .map(Outcome.Transcribed.class::cast);
  }
}
