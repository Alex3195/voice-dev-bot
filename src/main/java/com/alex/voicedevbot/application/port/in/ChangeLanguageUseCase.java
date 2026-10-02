package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;

/** Foydalanuvchi qaysi tilda gapirishini sozlash (STT'ga beriladi). */
public interface ChangeLanguageUseCase {

  LanguageCommandResult currentLanguage(TelegramUserId user);

  LanguageCommandResult changeLanguage(TelegramUserId user, SpeechLanguage language);
}
