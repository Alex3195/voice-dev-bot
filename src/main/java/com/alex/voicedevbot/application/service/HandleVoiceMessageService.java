package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import com.alex.voicedevbot.application.port.in.VoiceHandlingResult;
import com.alex.voicedevbot.application.port.in.VoiceMessage;
import com.alex.voicedevbot.application.port.out.AudioSource;
import com.alex.voicedevbot.application.port.out.SpeechToText;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.AudioClip;
import java.util.Objects;

public class HandleVoiceMessageService implements HandleVoiceMessageUseCase {

  private final AccessPolicy accessPolicy;
  private final AudioSource audioSource;
  private final SpeechToText speechToText;

  public HandleVoiceMessageService(
      AccessPolicy accessPolicy, AudioSource audioSource, SpeechToText speechToText) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.audioSource = Objects.requireNonNull(audioSource, "audioSource");
    this.speechToText = Objects.requireNonNull(speechToText, "speechToText");
  }

  @Override
  public VoiceHandlingResult handle(VoiceMessage message) {
    if (!accessPolicy.isAllowed(message.sender())) {
      return new VoiceHandlingResult.AccessDenied();
    }
    AudioClip audio = audioSource.fetch(message.audio());
    return new VoiceHandlingResult.Transcribed(speechToText.transcribe(audio));
  }
}
