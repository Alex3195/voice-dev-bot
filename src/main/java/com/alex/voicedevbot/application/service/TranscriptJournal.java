package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.out.AudioArchive;
import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.application.port.out.TranscriptionLog;
import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.SourceAudio;
import com.alex.voicedevbot.domain.TranscriptRecord;
import com.alex.voicedevbot.domain.Transcription;
import com.alex.voicedevbot.domain.UserSettings;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Audio'ni arxivga, transkriptni jurnalga yozadi. Bu — qo'shimcha: yozib bo'lmasa foydalanuvchi
 * baribir matnni oladi, xato faqat log qilinadi.
 *
 * <p>{@code application} SLF4J'ga bog'lana olmaydi (ArchUnit), shuning uchun JDK'ning {@link
 * System.Logger}i — Spring Boot uni SLF4J'ga yo'naltiradi.
 */
public class TranscriptJournal {

  private static final System.Logger LOG = System.getLogger(TranscriptJournal.class.getName());

  private final AudioArchive archive;
  private final TranscriptionLog log;
  private final Clock clock;

  public TranscriptJournal(AudioArchive archive, TranscriptionLog log, Clock clock) {
    this.archive = Objects.requireNonNull(archive, "archive");
    this.log = Objects.requireNonNull(log, "log");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * @return jurnaldagi yozuv raqami; yozib bo'lmasa — bo'sh
   */
  public OptionalLong record(
      UserSettings speaker, SourceAudio source, AudioClip clip, Transcription transcription) {
    Instant now = clock.instant();
    TranscriptRecord record = TranscriptRecord.of(speaker, transcription, source, now);
    try {
      record = record.withArchivePath(archive.store(clip, now));
    } catch (StorageException e) {
      LOG.log(Level.WARNING, "Failed to archive " + clip + ", logging transcript without it", e);
    }
    try {
      return OptionalLong.of(log.append(record));
    } catch (StorageException e) {
      LOG.log(Level.WARNING, "Failed to log transcript " + record, e);
      return OptionalLong.empty();
    }
  }
}
