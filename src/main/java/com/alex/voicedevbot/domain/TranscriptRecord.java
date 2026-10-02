package com.alex.voicedevbot.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Transkripsiya jurnalidagi yozuv: kim, qaysi project va tilda gapirgani, STT natijasi, audio.
 *
 * <p>Arxivga yozib bo'lmagan audio ham jurnalga tushadi — Telegram {@code file_id} orqali uni
 * baribir qayta ko'rsatish mumkin.
 */
public final class TranscriptRecord {

  private final UserSettings speaker;
  private final Transcription transcription;
  private final SourceAudio audio;
  private final String archivePath;
  private final Instant createdAt;

  private TranscriptRecord(
      UserSettings speaker,
      Transcription transcription,
      SourceAudio audio,
      String archivePath,
      Instant createdAt) {
    this.speaker = Objects.requireNonNull(speaker, "speaker");
    this.transcription = Objects.requireNonNull(transcription, "transcription");
    this.audio = Objects.requireNonNull(audio, "audio");
    this.archivePath = archivePath;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  /**
   * @param speaker gapirgan foydalanuvchi, uning tili va o'sha paytdagi faol projecti
   */
  public static TranscriptRecord of(
      UserSettings speaker, Transcription transcription, SourceAudio audio, Instant createdAt) {
    return new TranscriptRecord(speaker, transcription, audio, null, createdAt);
  }

  public TranscriptRecord withArchivePath(String path) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("Archive path must not be blank");
    }
    return new TranscriptRecord(speaker, transcription, audio, path, createdAt);
  }

  public TelegramUserId user() {
    return speaker.user();
  }

  public Optional<ProjectName> project() {
    return speaker.activeProject();
  }

  public SpeechLanguage language() {
    return speaker.language();
  }

  public Transcription transcription() {
    return transcription;
  }

  public SourceAudio audio() {
    return audio;
  }

  public Optional<String> archivePath() {
    return Optional.ofNullable(archivePath);
  }

  public Instant createdAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TranscriptRecord that
        && speaker.equals(that.speaker)
        && transcription.equals(that.transcription)
        && audio.equals(that.audio)
        && Objects.equals(archivePath, that.archivePath)
        && createdAt.equals(that.createdAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(speaker, transcription, audio, archivePath, createdAt);
  }

  @Override
  public String toString() {
    return "TranscriptRecord[user=%s, project=%s, createdAt=%s]"
        .formatted(user().value(), project().map(ProjectName::value).orElse("-"), createdAt);
  }
}
