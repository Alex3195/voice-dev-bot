package com.alex.voicedevbot.adapter.in.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.domain.AudioKind;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.SourceAudio;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.telegram.telegrambots.meta.api.objects.Audio;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Video;
import org.telegram.telegrambots.meta.api.objects.VideoNote;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.message.Message;

class IncomingAudioTest {

  @Test
  void should_extract_voice_with_duration_and_default_mime_type_when_telegram_sends_none() {
    Message message = new Message();
    message.setVoice(new Voice("voice-id", "u", 3, null, 10L));

    assertThat(IncomingAudio.from(message))
        .contains(
            new IncomingAudio(
                new SourceAudio(
                    new AudioRef("voice-id", "audio/ogg"), AudioKind.VOICE, Duration.ofSeconds(3)),
                10));
  }

  @Test
  void should_extract_audio_file_with_its_mime_type() {
    Audio audio = new Audio();
    audio.setFileId("audio-id");
    audio.setMimeType("audio/mp4");
    audio.setFileSize(500L);
    Message message = new Message();
    message.setAudio(audio);

    assertThat(IncomingAudio.from(message))
        .contains(new IncomingAudio(source("audio-id", "audio/mp4", AudioKind.AUDIO), 500));
  }

  @Test
  void should_extract_video_note_as_mp4() {
    VideoNote note = new VideoNote();
    note.setFileId("note-id");
    note.setFileSize(700);
    note.setDuration(42);
    Message message = new Message();
    message.setVideoNote(note);

    assertThat(IncomingAudio.from(message))
        .contains(
            new IncomingAudio(
                new SourceAudio(
                    new AudioRef("note-id", "video/mp4"),
                    AudioKind.VIDEO_NOTE,
                    Duration.ofSeconds(42)),
                700));
  }

  @Test
  void should_extract_video_with_unknown_size_and_duration_as_zero() {
    Video video = new Video();
    video.setFileId("video-id");
    Message message = new Message();
    message.setVideo(video);

    assertThat(IncomingAudio.from(message))
        .contains(new IncomingAudio(source("video-id", "video/mp4", AudioKind.VIDEO), 0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"audio/x-wav", "video/quicktime"})
  void should_extract_document_when_it_is_audio_or_video(String mimeType) {
    Message message = documentMessage(mimeType);

    assertThat(IncomingAudio.from(message))
        .contains(new IncomingAudio(source("doc-id", mimeType, AudioKind.DOCUMENT), 300));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"application/pdf", "image/png"})
  void should_ignore_document_when_it_is_not_audio_or_video(String mimeType) {
    assertThat(IncomingAudio.from(documentMessage(mimeType))).isEmpty();
  }

  @Test
  void should_ignore_message_without_media() {
    Message message = new Message();
    message.setText("salom");

    assertThat(IncomingAudio.from(message)).isEmpty();
  }

  @Test
  void should_exceed_download_limit_only_when_size_is_over_20_mb() {
    SourceAudio audio = source("id", "audio/ogg", AudioKind.VOICE);

    assertThat(new IncomingAudio(audio, IncomingAudio.MAX_DOWNLOAD_BYTES).exceedsDownloadLimit())
        .isFalse();
    assertThat(
            new IncomingAudio(audio, IncomingAudio.MAX_DOWNLOAD_BYTES + 1).exceedsDownloadLimit())
        .isTrue();
  }

  private static SourceAudio source(String fileId, String mimeType, AudioKind kind) {
    return new SourceAudio(new AudioRef(fileId, mimeType), kind, Duration.ZERO);
  }

  private static Message documentMessage(String mimeType) {
    Document document = new Document();
    document.setFileId("doc-id");
    document.setMimeType(mimeType);
    document.setFileSize(300L);
    Message message = new Message();
    message.setDocument(document);
    return message;
  }
}
