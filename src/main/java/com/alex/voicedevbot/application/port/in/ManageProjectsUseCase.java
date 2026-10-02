package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.TelegramUserId;

/** Projectlarni qo'shish, faol projectni tanlash va ro'yxatini ko'rish. */
public interface ManageProjectsUseCase {

  /** Yangi project qo'shiladi va foydalanuvchi uchun darhol faol qilinadi. */
  ProjectCommandResult addProject(TelegramUserId user, ProjectName name);

  ProjectCommandResult selectProject(TelegramUserId user, ProjectName name);

  ProjectCommandResult listProjects(TelegramUserId user);
}
