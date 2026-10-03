package com.alex.voicedevbot.support;

import com.alex.voicedevbot.application.port.in.DraftTaskUseCase;
import com.alex.voicedevbot.application.port.in.LanguageModelProblem;
import com.alex.voicedevbot.application.port.in.TaskDraftResult;
import com.alex.voicedevbot.domain.TelegramUserId;

/** Claude o'chiq ({@code ANTHROPIC_API_KEY} yo'q): har doim oddiy qoralama. */
public class NotConfiguredDrafter implements DraftTaskUseCase {

  @Override
  public TaskDraftResult fromTranscript(TelegramUserId user, long journalId) {
    return new TaskDraftResult.Failed(LanguageModelProblem.NOT_CONFIGURED);
  }

  @Override
  public void confirmCorrection(TelegramUserId user, long journalId) {}
}
