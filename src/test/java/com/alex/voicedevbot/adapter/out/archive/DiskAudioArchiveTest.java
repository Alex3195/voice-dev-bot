package com.alex.voicedevbot.adapter.out.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.domain.AudioClip;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DiskAudioArchiveTest {

  private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
  private static final AudioClip VOICE = new AudioClip(new byte[] {1, 2, 3}, "audio/ogg");

  @TempDir Path root;

  @Test
  void should_store_audio_under_local_date_folder_and_return_relative_path() throws IOException {
    // 2026-10-01 21:30 UTC — Toshkentda allaqachon 2-oktyabr
    Instant receivedAt = Instant.parse("2026-10-01T21:30:00Z");

    String path = new DiskAudioArchive(root, TASHKENT).store(VOICE, receivedAt);

    assertThat(path).matches("2026-10-02/[0-9a-f-]{36}\\.ogg");
    assertThat(Files.readAllBytes(root.resolve(path))).containsExactly(1, 2, 3);
  }

  @Test
  void should_give_every_audio_its_own_file() {
    DiskAudioArchive archive = new DiskAudioArchive(root, TASHKENT);
    Instant now = Instant.parse("2026-10-02T09:00:00Z");

    assertThat(archive.store(VOICE, now)).isNotEqualTo(archive.store(VOICE, now));
  }

  @Test
  void should_load_stored_audio_back() {
    DiskAudioArchive archive = new DiskAudioArchive(root, TASHKENT);
    String path = archive.store(VOICE, Instant.parse("2026-10-02T09:00:00Z"));

    assertThat(archive.load(path, "audio/ogg")).isEqualTo(VOICE);
  }

  @Test
  void should_fail_to_load_missing_empty_or_outside_file() throws IOException {
    DiskAudioArchive archive = new DiskAudioArchive(root, TASHKENT);
    Files.createFile(root.resolve("empty.ogg"));

    assertThatThrownBy(() -> archive.load("2026-10-02/none.ogg", "audio/ogg"))
        .isInstanceOf(StorageException.class);
    assertThatThrownBy(() -> archive.load("empty.ogg", "audio/ogg"))
        .isInstanceOf(StorageException.class);
    assertThatThrownBy(() -> archive.load("../secret.ogg", "audio/ogg"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("escapes");
  }

  @ParameterizedTest
  @CsvSource({
    "audio/ogg, ogg",
    "audio/ogg; codecs=opus, ogg",
    "AUDIO/MPEG, mp3",
    "video/mp4, mp4",
    "video/quicktime, mov",
    "application/x-unknown, bin"
  })
  void should_pick_extension_from_mime_type(String mimeType, String extension) {
    assertThat(DiskAudioArchive.extensionOf(new AudioClip(new byte[1], mimeType)))
        .isEqualTo(extension);
  }

  @Test
  void should_throw_storage_exception_when_folder_cannot_be_created() throws IOException {
    Path file = Files.writeString(root.resolve("not-a-dir"), "x");

    assertThatThrownBy(
            () ->
                new DiskAudioArchive(file, TASHKENT)
                    .store(VOICE, Instant.parse("2026-10-02T09:00:00Z")))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("Failed to archive AudioClip[size=3");
  }
}
