package com.alex.voicedevbot.adapter.out.archive;

import com.alex.voicedevbot.application.port.out.AudioArchive;
import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.domain.AudioClip;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Audio'ni diskka yozadi: {@code <root>/<yyyy-mm-dd>/<uuid>.<ext>}. Qaytgan yo'l {@code root}ga
 * nisbatan — arxivni boshqa joyga (yoki MinIO/S3'ga) ko'chirganda jurnal o'zgarmaydi.
 */
public class DiskAudioArchive implements AudioArchive {

  static final String UNKNOWN_EXTENSION = "bin";

  private static final Map<String, String> EXTENSIONS =
      Map.of(
          "audio/ogg", "ogg",
          "audio/mpeg", "mp3",
          "audio/mp4", "m4a",
          "audio/x-m4a", "m4a",
          "audio/wav", "wav",
          "audio/x-wav", "wav",
          "video/mp4", "mp4",
          "video/quicktime", "mov",
          "video/webm", "webm");

  private final Path root;
  private final ZoneId zone;

  /**
   * @param zone papka sanasi qaysi vaqt mintaqasida hisoblanadi
   */
  public DiskAudioArchive(Path root, ZoneId zone) {
    this.root = Objects.requireNonNull(root, "root");
    this.zone = Objects.requireNonNull(zone, "zone");
  }

  @Override
  public String store(AudioClip audio, Instant receivedAt) {
    String relative =
        LocalDate.ofInstant(receivedAt, zone) + "/" + UUID.randomUUID() + "." + extensionOf(audio);
    Path target = root.resolve(relative);
    try {
      Files.createDirectories(target.getParent());
      Files.write(target, audio.bytes(), StandardOpenOption.CREATE_NEW);
      return relative;
    } catch (IOException e) {
      throw new StorageException("Failed to archive " + audio, e);
    }
  }

  static String extensionOf(AudioClip audio) {
    String mimeType = audio.mimeType().toLowerCase(Locale.ROOT);
    int parameters = mimeType.indexOf(';');
    String bare = (parameters < 0 ? mimeType : mimeType.substring(0, parameters)).strip();
    return EXTENSIONS.getOrDefault(bare, UNKNOWN_EXTENSION);
  }
}
