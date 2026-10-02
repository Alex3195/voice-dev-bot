package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.BrowseTranscriptsUseCase;
import com.alex.voicedevbot.application.port.in.TranscriptsResult;
import com.alex.voicedevbot.application.port.out.TranscriptionLog;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.LoggedTranscript;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.TranscriptFilter;
import java.util.List;
import java.util.Objects;

public class BrowseTranscriptsService implements BrowseTranscriptsUseCase {

  static final int PAGE_SIZE = 8;

  private final AccessPolicy accessPolicy;
  private final TranscriptionLog log;

  public BrowseTranscriptsService(AccessPolicy accessPolicy, TranscriptionLog log) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.log = Objects.requireNonNull(log, "log");
  }

  @Override
  public TranscriptsResult list(TelegramUserId user, TranscriptFilter filter, int page) {
    if (!accessPolicy.isAllowed(user)) {
      return new TranscriptsResult.AccessDenied();
    }
    int current = Math.max(page, 0);
    // Bitta ortiqcha yozuv keyingi sahifa borligini sanashsiz bildiradi
    List<LoggedTranscript> rows = log.list(filter, current * PAGE_SIZE, PAGE_SIZE + 1);
    boolean hasNext = rows.size() > PAGE_SIZE;
    return new TranscriptsResult.Page(
        filter, hasNext ? rows.subList(0, PAGE_SIZE) : rows, current, hasNext);
  }

  @Override
  public TranscriptsResult open(TelegramUserId user, long id) {
    if (!accessPolicy.isAllowed(user)) {
      return new TranscriptsResult.AccessDenied();
    }
    return log.find(id)
        .<TranscriptsResult>map(TranscriptsResult.Opened::new)
        .orElseGet(TranscriptsResult.NotFound::new);
  }
}
