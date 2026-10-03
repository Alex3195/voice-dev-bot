package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.BenchmarkSpeechToTextUseCase;
import com.alex.voicedevbot.application.port.in.SttBenchmarkReport;
import com.alex.voicedevbot.application.port.in.SttBenchmarkReport.Outcome;
import com.alex.voicedevbot.application.port.in.SttBenchmarkReport.Sample;
import com.alex.voicedevbot.application.port.out.AudioArchive;
import com.alex.voicedevbot.application.port.out.SpeechToText;
import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.application.port.out.TranscriptionException;
import com.alex.voicedevbot.application.port.out.TranscriptionLog;
import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.LoggedTranscript;
import com.alex.voicedevbot.domain.TranscriptRecord;
import com.alex.voicedevbot.domain.TranscriptionHints;
import com.alex.voicedevbot.domain.UserSettings;
import com.alex.voicedevbot.domain.WordErrorRate;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Har bir tasdiqlangan audio arxivdan o'qiladi va har bir dvigateldan o'tkaziladi. Dvigatellarga
 * bir xil ishoralar beriladi: yozuvdagi til va o'sha projectning hozirgi lug'ati. Bitta audio yoki
 * dvigatel xatosi o'lchashni to'xtatmaydi — natijada "xato" bo'lib qoladi.
 */
public class SttBenchmarkService implements BenchmarkSpeechToTextUseCase {

  private static final System.Logger LOG = System.getLogger(SttBenchmarkService.class.getName());

  /**
   * @param name hisobotdagi nomi (masalan, {@code whisper.cpp large-v3-q5_0})
   */
  public record Engine(String name, SpeechToText speechToText) {

    public Engine {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(speechToText, "speechToText");
      if (name.isBlank() || name.equals(SttBenchmarkReport.JOURNAL)) {
        throw new IllegalArgumentException("Engine name must be unique and not blank: " + name);
      }
    }
  }

  private final TranscriptionLog log;
  private final AudioArchive archive;
  private final TranscriptionHintsResolver hints;
  private final List<Engine> engines;
  private final Clock clock;

  public SttBenchmarkService(
      TranscriptionLog log,
      AudioArchive archive,
      TranscriptionHintsResolver hints,
      List<Engine> engines,
      Clock clock) {
    this.log = Objects.requireNonNull(log, "log");
    this.archive = Objects.requireNonNull(archive, "archive");
    this.hints = Objects.requireNonNull(hints, "hints");
    this.engines = List.copyOf(engines);
    this.clock = Objects.requireNonNull(clock, "clock");
    if (this.engines.stream().map(Engine::name).distinct().count() != this.engines.size()) {
      throw new IllegalArgumentException("Engine names must be unique");
    }
  }

  @Override
  public SttBenchmarkReport run() {
    List<LoggedTranscript> confirmed = log.confirmed();
    List<Sample> samples = new ArrayList<>();
    for (int i = 0; i < confirmed.size(); i++) {
      LoggedTranscript entry = confirmed.get(i);
      LOG.log(
          Level.INFO,
          "STT benchmark: transcript #{0} ({1}/{2})",
          entry.id(),
          i + 1,
          confirmed.size());
      samples.add(sample(entry));
    }
    List<String> names = new ArrayList<>();
    names.add(SttBenchmarkReport.JOURNAL);
    engines.forEach(engine -> names.add(engine.name()));
    return new SttBenchmarkReport(names, samples);
  }

  private Sample sample(LoggedTranscript entry) {
    TranscriptRecord record = entry.record();
    String reference = entry.confirmedText().orElseThrow();
    Map<String, Outcome> outcomes = new LinkedHashMap<>();
    String journalText = record.transcription().transcript().text();
    outcomes.put(
        SttBenchmarkReport.JOURNAL,
        new Outcome.Transcribed(
            journalText, WordErrorRate.of(reference, journalText), Optional.empty()));
    Optional<AudioClip> audio = audioOf(entry, outcomes);
    if (audio.isPresent()) {
      TranscriptionHints current = hints.resolve(speakerOf(record));
      for (Engine engine : engines) {
        outcomes.put(engine.name(), transcribe(engine, audio.get(), current, reference, entry));
      }
    }
    return new Sample(
        entry.id(),
        record.project(),
        record.audio().duration(),
        reference,
        WordErrorRate.words(reference).size(),
        outcomes);
  }

  /** Audio bo'lmasa har bir dvigatel uchun sababi yoziladi. */
  private Optional<AudioClip> audioOf(LoggedTranscript entry, Map<String, Outcome> outcomes) {
    TranscriptRecord record = entry.record();
    String reason;
    if (record.archivePath().isEmpty()) {
      reason = "audio arxivga yozilmagan";
    } else {
      try {
        return Optional.of(
            archive.load(record.archivePath().get(), record.audio().ref().mimeType()));
      } catch (StorageException e) {
        LOG.log(Level.WARNING, "Could not read archived audio of transcript " + entry.id(), e);
        reason = "audio o'qilmadi: " + e.getMessage();
      }
    }
    for (Engine engine : engines) {
      outcomes.put(engine.name(), new Outcome.Failed(reason));
    }
    return Optional.empty();
  }

  private Outcome transcribe(
      Engine engine,
      AudioClip audio,
      TranscriptionHints current,
      String reference,
      LoggedTranscript entry) {
    Instant start = clock.instant();
    try {
      String text = engine.speechToText().transcribe(audio, current).transcript().text();
      return new Outcome.Transcribed(
          text,
          WordErrorRate.of(reference, text),
          Optional.of(Duration.between(start, clock.instant())));
    } catch (TranscriptionException e) {
      LOG.log(Level.WARNING, engine.name() + " failed on transcript " + entry.id(), e);
      return new Outcome.Failed(e.getMessage());
    }
  }

  private static UserSettings speakerOf(TranscriptRecord record) {
    UserSettings speaker = UserSettings.defaults(record.user(), record.language());
    return record.project().map(speaker::withActiveProject).orElse(speaker);
  }
}
