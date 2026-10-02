package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.NewTask;
import com.alex.voicedevbot.domain.TaskStatus;
import com.alex.voicedevbot.domain.TelegramUserId;

/**
 * Faol projectning tasklari — uning repo'sidagi Issue'lar. Holat xizmatdan har safar yangidan
 * o'qiladi (botda nusxa yo'q).
 */
public interface ManageTasksUseCase {

  /** Bot yaratgan task'lar shu label bilan belgilanadi — agent ularni shu bo'yicha oladi. */
  String AI_TASK_LABEL = "ai-task";

  /** Har holatda nechta task. */
  TasksResult overview(TelegramUserId user);

  /**
   * @param page 0 dan boshlanadi
   */
  TasksResult list(TelegramUserId user, TaskStatus status, int page);

  TasksResult open(TelegramUserId user, long iid);

  /** Issue {@link #AI_TASK_LABEL} label bilan yaratiladi. */
  TasksResult create(TelegramUserId user, NewTask task);

  /**
   * @param open {@code true} — qayta ochish, {@code false} — yopish
   */
  TasksResult setOpen(TelegramUserId user, long iid, boolean open);
}
