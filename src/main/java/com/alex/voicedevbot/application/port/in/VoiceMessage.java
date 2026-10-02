package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.SourceAudio;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.Objects;

/** Kiruvchi ovozli xabar: kim yubordi va audio qayerda. */
public record VoiceMessage(TelegramUserId sender, SourceAudio audio) {

  public VoiceMessage {
    Objects.requireNonNull(sender, "sender");
    Objects.requireNonNull(audio, "audio");
  }
}
