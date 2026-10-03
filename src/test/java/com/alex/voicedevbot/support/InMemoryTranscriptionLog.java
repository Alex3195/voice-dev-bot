package com.alex.voicedevbot.support;

import com.alex.voicedevbot.application.port.out.TranscriptionLog;
import com.alex.voicedevbot.domain.LlmUsage;
import com.alex.voicedevbot.domain.LoggedTranscript;
import com.alex.voicedevbot.domain.TranscriptFilter;
import com.alex.voicedevbot.domain.TranscriptRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link TranscriptionLog} shartnomasini xotirada bajaradi: eng yangisi birinchi. */
public class InMemoryTranscriptionLog implements TranscriptionLog {

  private final List<LoggedTranscript> entries = new ArrayList<>();
  private final Map<Long, LlmUsage> usage = new HashMap<>();
  private final Map<Long, String> corrected = new HashMap<>();
  private final Map<Long, String> confirmed = new HashMap<>();

  @Override
  public long append(TranscriptRecord record) {
    long id = entries.size() + 1L;
    entries.add(new LoggedTranscript(id, record));
    return id;
  }

  @Override
  public List<LoggedTranscript> list(TranscriptFilter filter, int offset, int limit) {
    return entries.stream()
        .filter(entry -> matches(filter, entry.record()))
        .sorted(
            Comparator.comparing((LoggedTranscript entry) -> entry.record().createdAt())
                .thenComparingLong(LoggedTranscript::id)
                .reversed())
        .skip(offset)
        .limit(limit)
        .map(this::withTexts)
        .toList();
  }

  @Override
  public Optional<LoggedTranscript> find(long id) {
    return entries.stream().filter(entry -> entry.id() == id).findFirst().map(this::withTexts);
  }

  @Override
  public void recordCorrection(long id, Optional<String> correctedText, LlmUsage llmUsage) {
    usage.put(id, llmUsage);
    correctedText.ifPresentOrElse(text -> corrected.put(id, text), () -> corrected.remove(id));
  }

  @Override
  public void confirmCorrection(long id) {
    Optional.ofNullable(corrected.get(id)).ifPresent(text -> confirmed.put(id, text));
  }

  public Optional<LlmUsage> usageOf(long id) {
    return Optional.ofNullable(usage.get(id));
  }

  public List<TranscriptRecord> records() {
    return entries.stream().map(LoggedTranscript::record).toList();
  }

  private LoggedTranscript withTexts(LoggedTranscript entry) {
    return new LoggedTranscript(
        entry.id(),
        entry.record(),
        Optional.ofNullable(corrected.get(entry.id())),
        Optional.ofNullable(confirmed.get(entry.id())));
  }

  private static boolean matches(TranscriptFilter filter, TranscriptRecord record) {
    return switch (filter) {
      case TranscriptFilter.OfProject(var project) ->
          record.project().filter(project::sameAs).isPresent();
      case TranscriptFilter.WithoutProject() -> record.project().isEmpty();
    };
  }
}
