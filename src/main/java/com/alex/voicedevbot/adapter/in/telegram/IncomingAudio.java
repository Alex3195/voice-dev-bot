package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.domain.AudioRef;
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
record IncomingAudio(AudioRef ref, long sizeBytes) {

  /** Oddiy Bot API {@code getFile} bundan katta faylni yuklab berishni rad etadi. */
  static final long MAX_DOWNLOAD_BYTES = 20L * 1024 * 1024;

  static final String DEFAULT_VOICE_MIME_TYPE = "audio/ogg";
  static final String DEFAULT_AUDIO_MIME_TYPE = "audio/mpeg";
  static final String DEFAULT_VIDEO_MIME_TYPE = "video/mp4";

  IncomingAudio {
    Objects.requireNonNull(ref, "ref");
  }

  boolean exceedsDownloadLimit() {
    return sizeBytes > MAX_DOWNLOAD_BYTES;
  }

  static Optional<IncomingAudio> from(Message message) {
    if (message.hasVoice()) {
      var voice = message.getVoice();
      return of(
          voice.getFileId(), voice.getMimeType(), DEFAULT_VOICE_MIME_TYPE, voice.getFileSize());
    }
    if (message.hasAudio()) {
      var audio = message.getAudio();
      return of(
          audio.getFileId(), audio.getMimeType(), DEFAULT_AUDIO_MIME_TYPE, audio.getFileSize());
    }
    if (message.hasVideoNote()) {
      var note = message.getVideoNote();
      return of(note.getFileId(), null, DEFAULT_VIDEO_MIME_TYPE, sizeOf(note.getFileSize()));
    }
    if (message.hasVideo()) {
      var video = message.getVideo();
      return of(
          video.getFileId(), video.getMimeType(), DEFAULT_VIDEO_MIME_TYPE, video.getFileSize());
    }
    return message.hasDocument() ? fromDocument(message.getDocument()) : Optional.empty();
  }

  private static Optional<IncomingAudio> fromDocument(Document document) {
    String mimeType = document.getMimeType();
    if (mimeType == null || !(mimeType.startsWith("audio/") || mimeType.startsWith("video/"))) {
      return Optional.empty();
    }
    return of(document.getFileId(), mimeType, mimeType, document.getFileSize());
  }

  private static Optional<IncomingAudio> of(
      String fileId, String mimeType, String defaultMimeType, Long sizeBytes) {
    String effectiveMimeType = mimeType == null ? defaultMimeType : mimeType;
    long size = sizeBytes == null ? 0 : sizeBytes;
    return Optional.of(new IncomingAudio(new AudioRef(fileId, effectiveMimeType), size));
  }

  private static Long sizeOf(Integer sizeBytes) {
    return sizeBytes == null ? null : sizeBytes.longValue();
  }
}
