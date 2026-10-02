package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.Transcript;

/** Ovozli xabarni qayta ishlash natijasi. */
public sealed interface VoiceHandlingResult {

  /** Ovoz muvaffaqiyatli matnga aylantirildi. */
  record Transcribed(Transcript transcript) implements VoiceHandlingResult {}

  /** Yuboruvchi whitelist'da yo'q. */
  record AccessDenied() implements VoiceHandlingResult {}
}
