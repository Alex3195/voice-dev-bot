package com.alex.voicedevbot.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.alex.voicedevbot.application.port.in.SttBenchmarkReport;
import com.alex.voicedevbot.application.port.in.SttBenchmarkReport.EngineSummary;
import com.alex.voicedevbot.application.port.in.SttBenchmarkReport.Outcome;
import com.alex.voicedevbot.application.port.in.SttBenchmarkReport.Sample;
import com.alex.voicedevbot.application.port.out.AudioArchive;
import com.alex.voicedevbot.application.port.out.SpeechToText;
import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.application.port.out.TranscriptionException;
import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.AudioKind;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.Glossary;
import com.alex.voicedevbot.domain.LlmUsage;
import com.alex.voicedevbot.domain.ModelId;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SourceAudio;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.Transcript;
import com.alex.voicedevbot.domain.TranscriptRecord;
import com.alex.voicedevbot.domain.Transcription;
import com.alex.voicedevbot.domain.TranscriptionHints;
import com.alex.voicedevbot.domain.UserSettings;
import com.alex.voicedevbot.domain.WordErrorRate.WordError;
import com.alex.voicedevbot.support.InMemoryProjectRepository;
import com.alex.voicedevbot.support.InMemoryTranscriptionLog;
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SttBenchmarkServiceTest {

  private static final TelegramUserId USER = new TelegramUserId(42);
  private static final ProjectName BILLS = new ProjectName("utility bills");
  private static final Instant AT = Instant.parse("2026-10-03T09:00:00Z");
  private static final AudioClip AUDIO = new AudioClip(new byte[] {1, 2}, "audio/ogg");
  private static final String CORRECT = "QR kod orqali to'lov qilish kerak";

  private final InMemoryTranscriptionLog log = new InMemoryTranscriptionLog();
  private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
  private final Map<String, AudioClip> stored = new java.util.HashMap<>();
  private final List<TranscriptionHints> heard = new ArrayList<>();

  private final AudioArchive archive =
      new AudioArchive() {
        @Override
        public String store(AudioClip audio, Instant receivedAt) {
          throw new UnsupportedOperationException();
        }

        @Override
        public AudioClip load(String path, String mimeType) {
          AudioClip clip = stored.get(path);
          if (clip == null) {
            throw new StorageException("no file " + path, null);
          }
          return clip;
        }
      };

  /** Har {@code instant()} chaqiruvida 2 soniya o'tadi — ishlov vaqti 2 soniya bo'ladi. */
  private final Clock ticking =
      new Clock() {
        private Instant now = AT;

        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          Instant current = now;
          now = now.plusSeconds(2);
          return current;
        }
      };

  private SpeechToText answering(String text) {
    return (audio, hints) -> {
      heard.add(hints);
      return new Transcription(new Transcript(text), "", "fake");
    };
  }

  private SttBenchmarkService service(List<SttBenchmarkService.Engine> engines) {
    return new SttBenchmarkService(
        log,
        archive,
        new TranscriptionHintsResolver(
            new UserSettingsLookup(new InMemoryUserSettingsRepository(), new SpeechLanguage("uz")),
            projects),
        engines,
        ticking);
  }

  /** Jurnalga yozib, tuzatib va tasdiqlab qo'yadi. */
  private long confirmed(String raw, String correct, String archivePath, int seconds) {
    TranscriptRecord record =
        TranscriptRecord.of(
            UserSettings.defaults(USER, new SpeechLanguage("uz")).withActiveProject(BILLS),
            new Transcription(new Transcript(raw), "prompt", "whisper"),
            new SourceAudio(
                new AudioRef("f", "audio/ogg"), AudioKind.VOICE, Duration.ofSeconds(seconds)),
            AT);
    long id = log.append(archivePath == null ? record : record.withArchivePath(archivePath));
    log.recordCorrection(
        id, Optional.of(correct), new LlmUsage(new ModelId("claude-opus-5-5"), 1, 0, 0, 1));
    log.confirmCorrection(id);
    return id;
  }

  @Test
  void should_compare_journal_and_engines_with_current_glossary() {
    projects.save(new Project(BILLS, Glossary.of(List.of("QR kod"))));
    long id = confirmed("kar kod orqali tolov qilish kerak", CORRECT, "a.ogg", 10);
    stored.put("a.ogg", AUDIO);

    SttBenchmarkReport report =
        service(
                List.of(
                    new SttBenchmarkService.Engine(
                        "q5", answering("QR kod orqali to'lov qilish kerak")),
                    new SttBenchmarkService.Engine(
                        "full", answering("qr kod orqali to'lov kerak"))))
            .run();

    assertThat(report.engines()).containsExactly(SttBenchmarkReport.JOURNAL, "q5", "full");
    Sample sample = report.samples().getFirst();
    assertThat(sample.journalId()).isEqualTo(id);
    assertThat(sample.project()).contains(BILLS);
    assertThat(sample.reference()).isEqualTo(CORRECT);
    assertThat(sample.referenceWords()).isEqualTo(6);
    assertThat(heard)
        .containsOnly(new TranscriptionHints(new SpeechLanguage("uz"), List.of("QR kod")));
    assertThat(report.summaries())
        .extracting(summary -> summary.wer().rate())
        .containsExactly(2 / 6.0, 0.0, 1 / 6.0);
    EngineSummary q5 = report.summaries().get(1);
    assertThat(q5.took()).contains(Duration.ofSeconds(2));
    assertThat(q5.realTimeFactor())
        .hasValueSatisfying(f -> assertThat(f).isCloseTo(0.2, within(1e-9)));
    assertThat(report.summaries().getFirst().took()).isEmpty();
    assertThat(report.summaries().getFirst().realTimeFactor()).isEmpty();
    assertThat(report.topErrors(SttBenchmarkReport.JOURNAL, 5))
        .extracting(Map.Entry::getKey)
        .containsExactly(new WordError("qr", "kar"), new WordError("to'lov", "tolov"));
  }

  @Test
  void should_keep_going_when_audio_or_engine_fails() {
    confirmed("bir", "bir", null, 5);
    confirmed("ikki", "ikki", "missing.ogg", 5);
    confirmed("uch", "uch", "c.ogg", 5);
    stored.put("c.ogg", AUDIO);
    SpeechToText broken =
        (audio, hints) -> {
          throw new TranscriptionException("whisper-server down", null);
        };

    SttBenchmarkReport report =
        service(List.of(new SttBenchmarkService.Engine("broken", broken))).run();

    assertThat(report.samples())
        .extracting(sample -> sample.outcomes().get("broken"))
        .containsExactly(
            new Outcome.Failed("audio arxivga yozilmagan"),
            new Outcome.Failed("audio o'qilmadi: no file missing.ogg"),
            new Outcome.Failed("whisper-server down"));
    EngineSummary summary = report.summaries().get(1);
    assertThat(summary.transcribed()).isZero();
    assertThat(summary.failed()).isEqualTo(3);
    assertThat(summary.took()).isEmpty();
    assertThat(report.summaries().getFirst().wer().rate()).isZero();
  }

  @Test
  void should_use_language_only_when_project_is_gone_and_skip_unconfirmed() {
    long id = confirmed("matn", "matn", "a.ogg", 3);
    stored.put("a.ogg", AUDIO);
    log.append(
        TranscriptRecord.of(
            UserSettings.defaults(USER, new SpeechLanguage("uz")),
            new Transcription(new Transcript("tasdiqlanmagan"), "", "whisper"),
            new SourceAudio(new AudioRef("g", "audio/ogg"), AudioKind.VOICE, Duration.ZERO),
            AT));

    SttBenchmarkReport report =
        service(List.of(new SttBenchmarkService.Engine("q5", answering("matn")))).run();

    assertThat(report.samples()).extracting(Sample::journalId).containsExactly(id);
    assertThat(heard).containsExactly(TranscriptionHints.languageOnly(new SpeechLanguage("uz")));
  }

  @Test
  void should_reject_blank_reserved_or_duplicate_engine_names() {
    SpeechToText stt = answering("x");
    assertThatThrownBy(() -> new SttBenchmarkService.Engine(" ", stt))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SttBenchmarkService.Engine(SttBenchmarkReport.JOURNAL, stt))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service(
                    List.of(
                        new SttBenchmarkService.Engine("q5", stt),
                        new SttBenchmarkService.Engine("q5", stt))))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
