package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import com.alex.voicedevbot.application.port.in.VoiceHandlingResult;
import com.alex.voicedevbot.application.port.in.VoiceMessage;
import com.alex.voicedevbot.application.port.out.AudioSource;
import com.alex.voicedevbot.application.port.out.SpeechToText;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.Transcription;
import com.alex.voicedevbot.domain.UserSettings;
import java.util.Objects;
import java.util.OptionalLong;

public class HandleVoiceMessageService implements HandleVoiceMessageUseCase {

  private final AccessPolicy accessPolicy;
  private final AudioSource audioSource;
  private final SpeechToText speechToText;
  private final TranscriptionHintsResolver hintsResolver;
  private final TranscriptJournal journal;

  public HandleVoiceMessageService(
      AccessPolicy accessPolicy,
      AudioSource audioSource,
      SpeechToText speechToText,
      TranscriptionHintsResolver hintsResolver,
      TranscriptJournal journal) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.audioSource = Objects.requireNonNull(audioSource, "audioSource");
    this.speechToText = Objects.requireNonNull(speechToText, "speechToText");
    this.hintsResolver = Objects.requireNonNull(hintsResolver, "hintsResolver");
    this.journal = Objects.requireNonNull(journal, "journal");
  }

  @Override
  public VoiceHandlingResult handle(VoiceMessage message) {
    if (!accessPolicy.isAllowed(message.sender())) {
      return new VoiceHandlingResult.AccessDenied();
    }
    AudioClip audio = audioSource.fetch(message.audio().ref());
    UserSettings speaker = hintsResolver.settingsOf(message.sender());
    Transcription transcription = speechToText.transcribe(audio, hintsResolver.resolve(speaker));
    OptionalLong journalId = journal.record(speaker, message.audio(), audio, transcription);
    return new VoiceHandlingResult.Transcribed(transcription.transcript(), journalId);
  }
}
