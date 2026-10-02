package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.domain.AudioKind;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.SourceAudio;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * Telegram xabaridagi matnga aylantiriladigan audio: voice, audio fayl, video, video xabar yoki
 * audio/video hujjat.
 *
 * @param sizeBytes Telegram bergan hajm; noma'lum bo'lsa {@code 0}
 */
record IncomingAudio(SourceAudio audio, long sizeBytes) {

  /** Oddiy Bot API {@code getFile} bundan katta faylni yuklab berishni rad etadi. */
  static final long MAX_DOWNLOAD_BYTES = 20L * 1024 * 1024;

  static final String DEFAULT_VOICE_MIME_TYPE = "audio/ogg";
  static final String DEFAULT_AUDIO_MIME_TYPE = "audio/mpeg";
  static final String DEFAULT_VIDEO_MIME_TYPE = "video/mp4";

  IncomingAudio {
    Objects.requireNonNull(audio, "audio");
  }

  boolean exceedsDownloadLimit() {
    return sizeBytes > MAX_DOWNLOAD_BYTES;
  }

  static Optional<IncomingAudio> from(Message message) {
    if (message.hasVoice()) {
      var voice = message.getVoice();
      return of(
          new Media(voice.getFileId(), voice.getMimeType(), AudioKind.VOICE, voice.getDuration()),
          DEFAULT_VOICE_MIME_TYPE,
          voice.getFileSize());
    }
    if (message.hasAudio()) {
      var audio = message.getAudio();
      return of(
          new Media(audio.getFileId(), audio.getMimeType(), AudioKind.AUDIO, audio.getDuration()),
          DEFAULT_AUDIO_MIME_TYPE,
          audio.getFileSize());
    }
    if (message.hasVideoNote()) {
      var note = message.getVideoNote();
      return of(
          new Media(note.getFileId(), null, AudioKind.VIDEO_NOTE, note.getDuration()),
          DEFAULT_VIDEO_MIME_TYPE,
          sizeOf(note.getFileSize()));
    }
    if (message.hasVideo()) {
      var video = message.getVideo();
      return of(
          new Media(video.getFileId(), video.getMimeType(), AudioKind.VIDEO, video.getDuration()),
          DEFAULT_VIDEO_MIME_TYPE,
          video.getFileSize());
    }
    return message.hasDocument() ? fromDocument(message.getDocument()) : Optional.empty();
  }

  private static Optional<IncomingAudio> fromDocument(Document document) {
    String mimeType = document.getMimeType();
    if (mimeType == null || !(mimeType.startsWith("audio/") || mimeType.startsWith("video/"))) {
      return Optional.empty();
    }
    return of(
        new Media(document.getFileId(), mimeType, AudioKind.DOCUMENT, null),
        mimeType,
        document.getFileSize());
  }

  private static Optional<IncomingAudio> of(Media media, String defaultMimeType, Long sizeBytes) {
    String mimeType = media.mimeType() == null ? defaultMimeType : media.mimeType();
    Duration duration =
        media.durationSeconds() == null
            ? Duration.ZERO
            : Duration.ofSeconds(media.durationSeconds());
    SourceAudio audio =
        new SourceAudio(new AudioRef(media.fileId(), mimeType), media.kind(), duration);
    return Optional.of(new IncomingAudio(audio, sizeBytes == null ? 0 : sizeBytes));
  }

  private static Long sizeOf(Integer sizeBytes) {
    return sizeBytes == null ? null : sizeBytes.longValue();
  }

  /** Telegram media turlaridan umumiy maydonlar; {@code null} — Telegram bermagan. */
  private record Media(String fileId, String mimeType, AudioKind kind, Integer durationSeconds) {}
}
