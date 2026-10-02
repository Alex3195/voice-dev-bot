package com.alex.voicedevbot.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.application.port.in.TranscriptsResult;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.AudioKind;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.LoggedTranscript;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SourceAudio;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.Transcript;
import com.alex.voicedevbot.domain.TranscriptFilter;
import com.alex.voicedevbot.domain.TranscriptRecord;
import com.alex.voicedevbot.domain.Transcription;
import com.alex.voicedevbot.domain.UserSettings;
import com.alex.voicedevbot.support.InMemoryTranscriptionLog;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BrowseTranscriptsServiceTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");
  private static final TranscriptFilter ELT = new TranscriptFilter.OfProject(ELT_IMZO);
  private static final Instant START = Instant.parse("2026-10-02T09:00:00Z");

  private final InMemoryTranscriptionLog log = new InMemoryTranscriptionLog();
  private final BrowseTranscriptsService service =
      new BrowseTranscriptsService(new AccessPolicy(Set.of(USER)), log);

  private void logTranscripts(int count) {
    UserSettings speaker =
        UserSettings.defaults(USER, new SpeechLanguage("uz")).withActiveProject(ELT_IMZO);
    for (int i = 0; i < count; i++) {
      log.append(
          TranscriptRecord.of(
              speaker,
              new Transcription(new Transcript("matn " + i), "", "stub"),
              new SourceAudio(new AudioRef("f" + i, "audio/ogg"), AudioKind.VOICE, Duration.ZERO),
              START.plusSeconds(i)));
    }
  }

  @Test
  void should_return_newest_first_page_and_report_next_page() {
    logTranscripts(BrowseTranscriptsService.PAGE_SIZE + 1);

    TranscriptsResult result = service.list(USER, ELT, 0);

    assertThat(result)
        .isInstanceOfSatisfying(
            TranscriptsResult.Page.class,
            page -> {
              assertThat(page.items()).hasSize(BrowseTranscriptsService.PAGE_SIZE);
              assertThat(page.items().getFirst().record().transcription().transcript().text())
                  .isEqualTo("matn 8");
              assertThat(page.hasNext()).isTrue();
            });
  }

  @Test
  void should_return_last_page_without_next() {
    logTranscripts(BrowseTranscriptsService.PAGE_SIZE + 1);

    TranscriptsResult result = service.list(USER, ELT, 1);

    assertThat(result)
        .isInstanceOfSatisfying(
            TranscriptsResult.Page.class,
            page -> {
              assertThat(page.items()).extracting(LoggedTranscript::id).containsExactly(1L);
              assertThat(page.page()).isEqualTo(1);
              assertThat(page.hasNext()).isFalse();
            });
  }

  @Test
  void should_filter_transcripts_without_project() {
    logTranscripts(2);

    TranscriptsResult result = service.list(USER, new TranscriptFilter.WithoutProject(), 0);

    assertThat(result)
        .isEqualTo(
            new TranscriptsResult.Page(new TranscriptFilter.WithoutProject(), List.of(), 0, false));
  }

  @Test
  void should_open_transcript_or_report_missing_one() {
    logTranscripts(1);

    assertThat(service.open(USER, 1)).isEqualTo(new TranscriptsResult.Opened(log.find(1).get()));
    assertThat(service.open(USER, 99)).isInstanceOf(TranscriptsResult.NotFound.class);
  }

  @Test
  void should_deny_stranger_to_list_or_open() {
    logTranscripts(1);

    assertThat(service.list(STRANGER, ELT, 0)).isInstanceOf(TranscriptsResult.AccessDenied.class);
    assertThat(service.open(STRANGER, 1)).isInstanceOf(TranscriptsResult.AccessDenied.class);
  }
}
