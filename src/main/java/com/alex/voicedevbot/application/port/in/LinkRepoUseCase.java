package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.TelegramUserId;

/** Foydalanuvchining faol projectini repo'ga bog'lash: mavjudini tanlash yoki yaratish. */
public interface LinkRepoUseCase {

  RepoLinkResult show(TelegramUserId user);

  RepoLinkResult search(TelegramUserId user, long connectionId, String query);

  RepoLinkResult namespaces(TelegramUserId user, long connectionId);

  RepoLinkResult link(TelegramUserId user, long connectionId, long repoId);

  /**
   * Havola bo'yicha: o'sha server uchun faol ulanish bo'lsa darhol ulanadi, yo'q bo'lsa — {@link
   * RepoLinkResult.NeedsConnection}, tugagan bo'lsa — {@link RepoLinkResult.NeedsNewToken}.
   *
   * @param url foydalanuvchi yuborgan havola ({@code https://github.com/egasi/nomi})
   */
  RepoLinkResult linkByUrl(TelegramUserId user, String url);

  /** Private repo yaratiladi, ichiga agent qoidalari va {@code docs/} tuzilmasi qo'yiladi. */
  RepoLinkResult create(TelegramUserId user, long connectionId, long namespaceId, String name);

  RepoLinkResult unlink(TelegramUserId user);
}
