package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.TelegramUserId;

/**
 * Faol project repo'sidagi hujjatlar: {@code CLAUDE.md}, {@code README.md} va {@code docs/}
 * ichidagi Markdown fayllar. Repo yagona manba — botda nusxa yo'q.
 */
public interface BrowseDocsUseCase {

  DocsResult list(TelegramUserId user);

  /**
   * @param path {@link DocsResult.Listed#paths} dan biri; boshqa fayl ochilmaydi
   */
  DocsResult open(TelegramUserId user, String path);
}
