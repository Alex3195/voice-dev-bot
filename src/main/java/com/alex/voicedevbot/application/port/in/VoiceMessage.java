package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.Objects;

/** Kiruvchi ovozli xabar: kim yubordi va audio qayerda. */
public record VoiceMessage(TelegramUserId sender, AudioRef audio) {

  public VoiceMessage {
    Objects.requireNonNull(sender, "sender");
    Objects.requireNonNull(audio, "audio");
  }
}
