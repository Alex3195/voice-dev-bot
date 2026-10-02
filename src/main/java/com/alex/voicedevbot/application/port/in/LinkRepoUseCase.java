package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.TelegramUserId;

/** Foydalanuvchining faol projectini GitLab repo'ga bog'lash: mavjudini tanlash yoki yaratish. */
public interface LinkRepoUseCase {

  RepoLinkResult show(TelegramUserId user);

  RepoLinkResult search(TelegramUserId user, long connectionId, String query);

  RepoLinkResult namespaces(TelegramUserId user, long connectionId);

  RepoLinkResult link(TelegramUserId user, long connectionId, long repoId);

  /** Private repo yaratiladi, ichiga agent qoidalari va {@code docs/} tuzilmasi qo'yiladi. */
  RepoLinkResult create(TelegramUserId user, long connectionId, long namespaceId, String name);

  RepoLinkResult unlink(TelegramUserId user);
}
