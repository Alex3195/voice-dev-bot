package com.alex.voicedevbot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TranscriptRecordTest {

  private static final AudioRef REF = new AudioRef("file-id", "audio/ogg");
  private static final SourceAudio SOURCE =
      new SourceAudio(REF, AudioKind.VOICE, Duration.ofSeconds(5));
  private static final Transcription TRANSCRIPTION =
      new Transcription(new Transcript("matn"), "prompt", "stub");
  private static final Instant AT = Instant.parse("2026-10-02T09:30:00Z");
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");
  private static final UserSettings SPEAKER =
      UserSettings.defaults(new TelegramUserId(7L), new SpeechLanguage("kk"))
          .withActiveProject(ELT_IMZO);

  @Test
  void should_take_user_language_and_project_from_speaker() {
    TranscriptRecord record = TranscriptRecord.of(SPEAKER, TRANSCRIPTION, SOURCE, AT);

    assertThat(record.user()).isEqualTo(new TelegramUserId(7L));
    assertThat(record.language()).isEqualTo(new SpeechLanguage("kk"));
    assertThat(record.project()).contains(ELT_IMZO);
    assertThat(record.transcription()).isEqualTo(TRANSCRIPTION);
    assertThat(record.audio()).isEqualTo(SOURCE);
    assertThat(record.createdAt()).isEqualTo(AT);
    assertThat(record.archivePath()).isEmpty();
  }

  @Test
  void should_add_archive_path_without_changing_original() {
    TranscriptRecord record = TranscriptRecord.of(SPEAKER, TRANSCRIPTION, SOURCE, AT);

    TranscriptRecord archived = record.withArchivePath("2026-10-02/a.ogg");

    assertThat(archived.archivePath()).contains("2026-10-02/a.ogg");
    assertThat(record.archivePath()).isEmpty();
    assertThat(archived).isNotEqualTo(record);
    assertThat(archived)
        .isEqualTo(
            TranscriptRecord.of(SPEAKER, TRANSCRIPTION, SOURCE, AT)
                .withArchivePath("2026-10-02/a.ogg"))
        .hasSameHashCodeAs(
            TranscriptRecord.of(SPEAKER, TRANSCRIPTION, SOURCE, AT)
                .withArchivePath("2026-10-02/a.ogg"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  void should_reject_blank_archive_path(String path) {
    TranscriptRecord record = TranscriptRecord.of(SPEAKER, TRANSCRIPTION, SOURCE, AT);

    assertThatThrownBy(() -> record.withArchivePath(path))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_not_expose_transcript_text_in_to_string() {
    assertThat(TranscriptRecord.of(SPEAKER, TRANSCRIPTION, SOURCE, AT).toString())
        .isEqualTo("TranscriptRecord[user=7, project=ELT imzo, createdAt=2026-10-02T09:30:00Z]")
        .doesNotContain("matn");
  }

  @Test
  void should_reject_negative_audio_duration() {
    assertThatThrownBy(() -> new SourceAudio(REF, AudioKind.VOICE, Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  void should_reject_blank_model(String model) {
    assertThatThrownBy(() -> new Transcription(new Transcript("matn"), "", model))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
