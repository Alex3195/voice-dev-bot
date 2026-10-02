package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.LlmUsage;
import com.alex.voicedevbot.domain.LoggedTranscript;
import com.alex.voicedevbot.domain.TranscriptFilter;
import com.alex.voicedevbot.domain.TranscriptRecord;
import java.util.List;
import java.util.Optional;

/**
 * Transkripsiyalar jurnali. Barcha metodlar saqlab/o'qib bo'lmasa {@link StorageException}
 * tashlaydi.
 */
public interface TranscriptionLog {

  /**
   * @return yozuv raqami
   */
  long append(TranscriptRecord record);

  /** Eng yangisi birinchi. */
  List<LoggedTranscript> list(TranscriptFilter filter, int offset, int limit);

  Optional<LoggedTranscript> find(long id);

  /** Transkriptdan task tuzgan Claude chaqiruvi narxi; har chaqiruvda ustiga yoziladi. */
  void recordLlmUsage(long id, LlmUsage usage);
}
