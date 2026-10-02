package com.alex.voicedevbot.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.application.port.in.VoiceHandlingResult;
import com.alex.voicedevbot.application.port.in.VoiceMessage;
import com.alex.voicedevbot.application.port.out.AudioArchive;
import com.alex.voicedevbot.application.port.out.AudioSource;
import com.alex.voicedevbot.application.port.out.SpeechToText;
import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.application.port.out.TranscriptionLog;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.AudioKind;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.Glossary;
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
import com.alex.voicedevbot.support.InMemoryProjectRepository;
import com.alex.voicedevbot.support.InMemoryTranscriptionLog;
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HandleVoiceMessageServiceTest {

  private static final TelegramUserId ALLOWED = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final SourceAudio SOURCE =
      new SourceAudio(new AudioRef("file-id", "audio/ogg"), AudioKind.VOICE, Duration.ofSeconds(4));
  private static final AudioClip AUDIO = new AudioClip(new byte[] {1, 2, 3}, "audio/ogg");
  private static final SpeechLanguage UZ = new SpeechLanguage("uz");
  private static final Instant NOW = Instant.parse("2026-10-02T09:30:00Z");

  private final AudioSource audioSource = mock(AudioSource.class);
  private final SpeechToText speechToText = mock(SpeechToText.class);
  private final AudioArchive archive = mock(AudioArchive.class);
  private final InMemoryTranscriptionLog log = new InMemoryTranscriptionLog();
  private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
  private final InMemoryUserSettingsRepository settings = new InMemoryUserSettingsRepository();

  @BeforeEach
  void archiveAcceptsAudio() {
    when(archive.store(any(), any())).thenReturn("2026-10-02/a.ogg");
  }

  private HandleVoiceMessageService serviceWith(TranscriptionLog transcriptionLog) {
    return new HandleVoiceMessageService(
        new AccessPolicy(Set.of(ALLOWED)),
        audioSource,
        speechToText,
        new TranscriptionHintsResolver(new UserSettingsLookup(settings, UZ), projects),
        new TranscriptJournal(archive, transcriptionLog, Clock.fixed(NOW, ZoneOffset.UTC)));
  }

  private static Transcription transcription(String text) {
    return new Transcription(new Transcript(text), "prompt", "whisper.cpp large-v3");
  }

  @Test
  void should_return_transcript_with_default_language_when_user_has_no_settings() {
    // given
    when(audioSource.fetch(SOURCE.ref())).thenReturn(AUDIO);
    when(speechToText.transcribe(AUDIO, TranscriptionHints.languageOnly(UZ)))
        .thenReturn(transcription("login sahifasini tuzat"));

    // when
    VoiceHandlingResult result = serviceWith(log).handle(new VoiceMessage(ALLOWED, SOURCE));

    // then
    assertThat(result)
        .isEqualTo(new VoiceHandlingResult.Transcribed(new Transcript("login sahifasini tuzat")));
  }

  @Test
  void should_pass_user_language_and_active_project_glossary_to_stt() {
    // given
    ProjectName eltImzo = new ProjectName("ELT imzo");
    projects.save(new Project(eltImzo, Glossary.of(List.of("kassa bo'limi", "Klaes"))));
    SpeechLanguage kazakh = new SpeechLanguage("kk");
    settings.save(UserSettings.defaults(ALLOWED, kazakh).withActiveProject(eltImzo));
    TranscriptionHints expected = new TranscriptionHints(kazakh, List.of("kassa bo'limi", "Klaes"));
    when(audioSource.fetch(SOURCE.ref())).thenReturn(AUDIO);
    when(speechToText.transcribe(AUDIO, expected)).thenReturn(transcription("matn"));

    // when
    VoiceHandlingResult result = serviceWith(log).handle(new VoiceMessage(ALLOWED, SOURCE));

    // then
    assertThat(result).isEqualTo(new VoiceHandlingResult.Transcribed(new Transcript("matn")));
  }

  @Test
  void should_archive_audio_and_log_transcript_with_speaker_context() {
    // given
    ProjectName eltImzo = new ProjectName("ELT imzo");
    projects.save(Project.named(eltImzo));
    UserSettings speaker = UserSettings.defaults(ALLOWED, UZ).withActiveProject(eltImzo);
    settings.save(speaker);
    when(audioSource.fetch(SOURCE.ref())).thenReturn(AUDIO);
    when(speechToText.transcribe(any(), any())).thenReturn(transcription("matn"));

    // when
    serviceWith(log).handle(new VoiceMessage(ALLOWED, SOURCE));

    // then
    verify(archive).store(AUDIO, NOW);
    assertThat(log.records())
        .containsExactly(
            TranscriptRecord.of(speaker, transcription("matn"), SOURCE, NOW)
                .withArchivePath("2026-10-02/a.ogg"));
  }

  @Test
  void should_log_transcript_without_audio_path_when_archive_fails() {
    when(audioSource.fetch(SOURCE.ref())).thenReturn(AUDIO);
    when(speechToText.transcribe(any(), any())).thenReturn(transcription("matn"));
    when(archive.store(any(), any())).thenThrow(new StorageException("disk full", null));

    VoiceHandlingResult result = serviceWith(log).handle(new VoiceMessage(ALLOWED, SOURCE));

    assertThat(result).isEqualTo(new VoiceHandlingResult.Transcribed(new Transcript("matn")));
    assertThat(log.records()).singleElement().satisfies(r -> assertThat(r.archivePath()).isEmpty());
  }

  @Test
  void should_still_return_transcript_when_log_cannot_be_written() {
    TranscriptionLog broken = mock(TranscriptionLog.class);
    when(broken.append(any())).thenThrow(new StorageException("db down", null));
    when(audioSource.fetch(SOURCE.ref())).thenReturn(AUDIO);
    when(speechToText.transcribe(any(), any())).thenReturn(transcription("matn"));

    VoiceHandlingResult result = serviceWith(broken).handle(new VoiceMessage(ALLOWED, SOURCE));

    assertThat(result).isEqualTo(new VoiceHandlingResult.Transcribed(new Transcript("matn")));
  }

  @Test
  void should_deny_without_downloading_or_logging_when_sender_is_not_whitelisted() {
    VoiceHandlingResult result = serviceWith(log).handle(new VoiceMessage(STRANGER, SOURCE));

    assertThat(result).isInstanceOf(VoiceHandlingResult.AccessDenied.class);
    verifyNoInteractions(audioSource, speechToText, archive);
    assertThat(log.records()).isEmpty();
  }
}
