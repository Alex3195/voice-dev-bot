package com.alex.voicedevbot.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.application.port.in.VoiceHandlingResult;
import com.alex.voicedevbot.application.port.in.VoiceMessage;
import com.alex.voicedevbot.application.port.out.AudioSource;
import com.alex.voicedevbot.application.port.out.SpeechToText;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.Transcript;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HandleVoiceMessageServiceTest {

  private static final TelegramUserId ALLOWED = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final AudioRef AUDIO_REF = new AudioRef("file-id", "audio/ogg");
  private static final AudioClip AUDIO = new AudioClip(new byte[] {1, 2, 3}, "audio/ogg");

  private final AudioSource audioSource = mock(AudioSource.class);
  private final SpeechToText speechToText = mock(SpeechToText.class);
  private final HandleVoiceMessageService service =
      new HandleVoiceMessageService(new AccessPolicy(Set.of(ALLOWED)), audioSource, speechToText);

  @Test
  void should_return_transcript_when_sender_is_whitelisted() {
    // given
    when(audioSource.fetch(AUDIO_REF)).thenReturn(AUDIO);
    when(speechToText.transcribe(AUDIO)).thenReturn(new Transcript("login sahifasini tuzat"));

    // when
    VoiceHandlingResult result = service.handle(new VoiceMessage(ALLOWED, AUDIO_REF));

    // then
    assertThat(result)
        .isEqualTo(new VoiceHandlingResult.Transcribed(new Transcript("login sahifasini tuzat")));
  }

  @Test
  void should_deny_without_downloading_audio_when_sender_is_not_whitelisted() {
    VoiceHandlingResult result = service.handle(new VoiceMessage(STRANGER, AUDIO_REF));

    assertThat(result).isInstanceOf(VoiceHandlingResult.AccessDenied.class);
    verifyNoInteractions(audioSource, speechToText);
  }
}
