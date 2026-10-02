package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.TelegramUserId;

/**
 * Transkriptdan Claude bilan task qoralamasi. Qoralama faqat ko'rsatiladi — task {@link
 * ManageTasksUseCase#create} bilan, foydalanuvchi tasdiqlagach yaratiladi.
 */
public interface DraftTaskUseCase {

  TaskDraftResult fromTranscript(TelegramUserId user, long journalId);
}
