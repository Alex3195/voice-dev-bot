package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.AudioClip;
import java.time.Instant;

/** Audio'ni muddatsiz saqlaydi: lug'at takliflari, sozlamalarni o'lchash va fine-tuning uchun. */
public interface AudioArchive {

  /**
   * @param receivedAt audio qabul qilingan vaqt (arxivni sana bo'yicha tartiblash uchun)
   * @return saqlangan audio'ning arxiv ichidagi yo'li
   * @throws StorageException saqlab bo'lmasa
   */
  String store(AudioClip audio, Instant receivedAt);
}
