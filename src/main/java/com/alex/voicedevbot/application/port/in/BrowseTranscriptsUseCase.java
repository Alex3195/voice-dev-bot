package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.TranscriptFilter;

/** Transkripsiya jurnalini ko'rish: sahifalangan ro'yxat va bitta yozuv. */
public interface BrowseTranscriptsUseCase {

  /**
   * @param page 0 dan boshlanadi
   */
  TranscriptsResult list(TelegramUserId user, TranscriptFilter filter, int page);

  TranscriptsResult open(TelegramUserId user, long id);
}
