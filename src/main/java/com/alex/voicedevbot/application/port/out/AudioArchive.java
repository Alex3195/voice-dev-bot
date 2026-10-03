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

  /**
   * @param path {@link #store} qaytargan yo'l
   * @param mimeType audio turi (arxivda alohida saqlanmaydi — jurnaldan)
   * @throws StorageException fayl yo'q yoki o'qib bo'lmasa
   */
  AudioClip load(String path, String mimeType);
}
