package com.alex.voicedevbot.application.port.in;

import com.alex.voicedevbot.domain.LoggedTranscript;
import com.alex.voicedevbot.domain.TranscriptFilter;
import java.util.List;
import java.util.Objects;

/** Transkripsiya jurnalini ko'rish natijasi. */
public sealed interface TranscriptsResult {

  /**
   * @param page 0 dan boshlanadi
   * @param hasNext keyingi sahifa bormi
   */
  record Page(TranscriptFilter filter, List<LoggedTranscript> items, int page, boolean hasNext)
      implements TranscriptsResult {

    public Page {
      Objects.requireNonNull(filter, "filter");
      items = List.copyOf(items);
    }
  }

  record Opened(LoggedTranscript transcript) implements TranscriptsResult {}

  record NotFound() implements TranscriptsResult {}

  /** Yuboruvchi whitelist'da yo'q. */
  record AccessDenied() implements TranscriptsResult {}
}
