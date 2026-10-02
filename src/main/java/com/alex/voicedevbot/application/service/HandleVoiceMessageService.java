package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import com.alex.voicedevbot.application.port.in.VoiceHandlingResult;
import com.alex.voicedevbot.application.port.in.VoiceMessage;
import com.alex.voicedevbot.application.port.out.AudioSource;
import com.alex.voicedevbot.application.port.out.SpeechToText;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.TranscriptionHints;
import java.util.Objects;

public class HandleVoiceMessageService implements HandleVoiceMessageUseCase {

  private final AccessPolicy accessPolicy;
  private final AudioSource audioSource;
  private final SpeechToText speechToText;
  private final TranscriptionHintsResolver hintsResolver;

  public HandleVoiceMessageService(
      AccessPolicy accessPolicy,
      AudioSource audioSource,
      SpeechToText speechToText,
      TranscriptionHintsResolver hintsResolver) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.audioSource = Objects.requireNonNull(audioSource, "audioSource");
    this.speechToText = Objects.requireNonNull(speechToText, "speechToText");
    this.hintsResolver = Objects.requireNonNull(hintsResolver, "hintsResolver");
  }

  @Override
  public VoiceHandlingResult handle(VoiceMessage message) {
    if (!accessPolicy.isAllowed(message.sender())) {
      return new VoiceHandlingResult.AccessDenied();
    }
    AudioClip audio = audioSource.fetch(message.audio());
    TranscriptionHints hints = hintsResolver.resolve(message.sender());
    return new VoiceHandlingResult.Transcribed(speechToText.transcribe(audio, hints));
  }
}
