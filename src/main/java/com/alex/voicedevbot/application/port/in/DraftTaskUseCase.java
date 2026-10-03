package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.TelegramUserId;

/**
 * Transkriptdan Claude bilan task qoralamasi. Qoralama faqat ko'rsatiladi — task {@link
 * ManageTasksUseCase#create} bilan, foydalanuvchi tasdiqlagach yaratiladi.
 */
public interface DraftTaskUseCase {

  TaskDraftResult fromTranscript(TelegramUserId user, long journalId);

  /**
   * Shu transkriptdan Claude tuzgan task yaratildi — tuzatilgan matn tasdiqlangan deb belgilanadi.
   * Jurnalga yozib bo'lmasa task ishiga ta'sir qilmaydi.
   */
  void confirmCorrection(TelegramUserId user, long journalId);
}
