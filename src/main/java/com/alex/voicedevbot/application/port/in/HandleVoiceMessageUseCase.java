package com.alex.voicedevbot.application.port.in;

/** Ovozli xabarni qabul qilib, uni matnga aylantirish. */
public interface HandleVoiceMessageUseCase {

  VoiceHandlingResult handle(VoiceMessage message);
}
