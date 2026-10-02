package com.alex.voicedevbot.application.service;

import static com.alex.voicedevbot.support.GitLabFixtures.REPO;
import static com.alex.voicedevbot.support.GitLabFixtures.TOKEN;
import static com.alex.voicedevbot.support.GitLabFixtures.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.application.port.in.LanguageModelProblem;
import com.alex.voicedevbot.application.port.in.TaskDraftResult;
import com.alex.voicedevbot.application.port.out.IntegrationException;
import com.alex.voicedevbot.application.port.out.LanguageModelException;
import com.alex.voicedevbot.application.port.out.LanguageModelException.Reason;
import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.application.port.out.TaskParser;
import com.alex.voicedevbot.application.port.out.TaskParser.ParsedTask;
import com.alex.voicedevbot.application.port.out.TaskParser.ProjectBrief;
import com.alex.voicedevbot.application.port.out.TaskParser.RuleFile;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.AudioKind;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.Glossary;
import com.alex.voicedevbot.domain.LlmUsage;
import com.alex.voicedevbot.domain.ModelId;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.Provider;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.RepoLink;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.SourceAudio;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TaskDraft;
import com.alex.voicedevbot.domain.TaskType;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.TermCorrection;
import com.alex.voicedevbot.domain.Transcript;
import com.alex.voicedevbot.domain.TranscriptRecord;
import com.alex.voicedevbot.domain.Transcription;
import com.alex.voicedevbot.domain.UserSettings;
import com.alex.voicedevbot.support.CodeHostAndTracker;
import com.alex.voicedevbot.support.GitLabFixtures;
import com.alex.voicedevbot.support.InMemoryConnectionRepository;
import com.alex.voicedevbot.support.InMemoryProjectRepoLinks;
import com.alex.voicedevbot.support.InMemoryProjectRepository;
import com.alex.voicedevbot.support.InMemoryTranscriptionLog;
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DraftTaskServiceTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");
  private static final ProjectName FINBANK = new ProjectName("Finbank");
  private static final ModelId DEFAULT = new ModelId("claude-opus-5-5");
  private static final ModelId SONNET = new ModelId("claude-sonnet-5-5");
  private static final LlmUsage USAGE = new LlmUsage(DEFAULT, 120, 900, 0, 210);
  private static final String SPOKEN = "elt imza sahifasida muddat chiqsin";

  private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
  private final InMemoryUserSettingsRepository settingsRepository =
      new InMemoryUserSettingsRepository();
  private final InMemoryConnectionRepository connections = new InMemoryConnectionRepository();
  private final InMemoryProjectRepoLinks links = new InMemoryProjectRepoLinks();
  private final InMemoryTranscriptionLog log = new InMemoryTranscriptionLog();
  private final CodeHostAndTracker api = mock(CodeHostAndTracker.class);
  private final AtomicReference<TaskParser.Request> sent = new AtomicReference<>();
  private ParsedTask answer;
  private final DraftTaskService service = service(log);

  private DraftTaskService service(
      com.alex.voicedevbot.application.port.out.TranscriptionLog journal) {
    AccessPolicy access = new AccessPolicy(Set.of(USER));
    UserSettingsLookup settings =
        new UserSettingsLookup(settingsRepository, new SpeechLanguage("uz"));
    Clock clock =
        Clock.fixed(GitLabFixtures.TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    return new DraftTaskService(
        access,
        settings,
        projects,
        journal,
        new ProjectRepoAccess(
            access, settings, connections, links, GitLabFixtures.integrations(api), clock),
        request -> {
          sent.set(request);
          return answer;
        },
        DEFAULT);
  }

  private static TaskDraft draft(List<TermCorrection> corrections) {
    return new TaskDraft(
        "Sertifikat muddatini ko'rsatish",
        "Imzolash sahifasida muddat ko'rinsin.",
        List.of("Muddat sanasi ko'rinadi"),
        TaskType.FEATURE,
        corrections,
        "Muddat ko'rsatiladi.");
  }

  private long logged(String text) {
    return log.append(
        TranscriptRecord.of(
            UserSettings.defaults(USER, new SpeechLanguage("uz")),
            new Transcription(new Transcript(text), "", "whisper"),
            new SourceAudio(new AudioRef("f", "audio/ogg"), AudioKind.VOICE, Duration.ZERO),
            Instant.parse("2026-10-02T09:00:00Z")));
  }

  @BeforeEach
  void projectsAndActiveProject() {
    projects.save(new Project(ELT_IMZO, Glossary.of(List.of("ELT imzo", "PVX"))));
    projects.save(new Project(FINBANK, Glossary.of(List.of("Klaes"))));
    settingsRepository.save(
        UserSettings.defaults(USER, new SpeechLanguage("uz")).withActiveProject(ELT_IMZO));
    answer = new ParsedTask(draft(List.of()), Optional.of("ELT imzo"), USAGE);
  }

  @Test
  void should_send_transcript_glossaries_and_repo_rules_with_default_model() {
    // given
    ProviderConnection connection =
        connections.save(Provider.GITLAB, ServerAddress.GITLAB_COM, TOKEN, VALID);
    links.link(ELT_IMZO, new RepoLink(connection.id(), REPO));
    when(api.readFile(connection, REPO, "CLAUDE.md")).thenReturn(Optional.of("# Qoidalar"));
    when(api.readFile(connection, REPO, ".ai/criteria.yml")).thenReturn(Optional.empty());
    long id = logged(SPOKEN);

    // when
    TaskDraftResult result = service.fromTranscript(USER, id);

    // then
    assertThat(result)
        .isEqualTo(new TaskDraftResult.Drafted(answer.draft(), Optional.empty(), List.of(), USAGE));
    assertThat(sent.get())
        .isEqualTo(
            new TaskParser.Request(
                DEFAULT,
                SPOKEN,
                List.of(
                    new ProjectBrief(ELT_IMZO, List.of("ELT imzo", "PVX")),
                    new ProjectBrief(FINBANK, List.of("Klaes"))),
                Optional.of(ELT_IMZO),
                List.of(new RuleFile("CLAUDE.md", "# Qoidalar"))));
    assertThat(log.usageOf(id)).contains(USAGE);
  }

  @Test
  void should_use_chosen_model_and_go_without_rules_when_repo_is_unreachable() {
    settingsRepository.save(
        UserSettings.defaults(USER, new SpeechLanguage("uz"))
            .withActiveProject(ELT_IMZO)
            .withModel(SONNET));
    ProviderConnection connection =
        connections.save(Provider.GITLAB, ServerAddress.GITLAB_COM, TOKEN, VALID);
    links.link(ELT_IMZO, new RepoLink(connection.id(), REPO));
    when(api.readFile(any(), any(), any()))
        .thenThrow(new IntegrationException(IntegrationException.Reason.UNAVAILABLE, "x", null));

    assertThat(service.fromTranscript(USER, logged(SPOKEN)))
        .isInstanceOf(TaskDraftResult.Drafted.class);
    assertThat(sent.get().model()).isEqualTo(SONNET);
    assertThat(sent.get().rules()).isEmpty();
  }

  @Test
  void should_point_out_other_project_and_suggest_up_to_three_new_terms() {
    answer =
        new ParsedTask(
            draft(
                List.of(
                    new TermCorrection("elt imza", "ELT imzo"),
                    new TermCorrection("klayes", "Klaes"),
                    new TermCorrection("pevex", "Pvx"),
                    new TermCorrection("akva", "Akfa"),
                    new TermCorrection("bi-ai", "BI"),
                    new TermCorrection("dash bord", "Dashboard"))),
            Optional.of(" finbank "),
            USAGE);

    TaskDraftResult result = service.fromTranscript(USER, logged(SPOKEN));

    assertThat(result)
        .isInstanceOfSatisfying(
            TaskDraftResult.Drafted.class,
            drafted -> {
              assertThat(drafted.otherProject()).contains(FINBANK);
              assertThat(drafted.suggestedTerms()).containsExactly("Klaes", "Akfa", "BI");
            });
  }

  @Test
  void should_ignore_unknown_project_and_skip_suggestions_without_active_project() {
    settingsRepository.save(UserSettings.defaults(USER, new SpeechLanguage("uz")));
    answer =
        new ParsedTask(
            draft(List.of(new TermCorrection("akva", "Akfa"))), Optional.of("Nomalum"), USAGE);

    TaskDraftResult result = service.fromTranscript(USER, logged(SPOKEN));

    assertThat(result)
        .isEqualTo(new TaskDraftResult.Drafted(answer.draft(), Optional.empty(), List.of(), USAGE));
    assertThat(sent.get().activeProject()).isEmpty();
  }

  @Test
  void should_return_draft_even_when_usage_cannot_be_recorded() {
    InMemoryTranscriptionLog broken =
        new InMemoryTranscriptionLog() {
          @Override
          public void recordLlmUsage(long id, LlmUsage usage) {
            throw new StorageException("db down", null);
          }
        };
    long id =
        broken.append(
            TranscriptRecord.of(
                UserSettings.defaults(USER, new SpeechLanguage("uz")),
                new Transcription(new Transcript(SPOKEN), "", "whisper"),
                new SourceAudio(new AudioRef("f", "audio/ogg"), AudioKind.VOICE, Duration.ZERO),
                Instant.parse("2026-10-02T09:00:00Z")));

    assertThat(service(broken).fromTranscript(USER, id))
        .isInstanceOf(TaskDraftResult.Drafted.class);
  }

  @ParameterizedTest
  @EnumSource(Reason.class)
  void should_translate_claude_failures(Reason reason) {
    DraftTaskService failing =
        new DraftTaskService(
            new AccessPolicy(Set.of(USER)),
            new UserSettingsLookup(settingsRepository, new SpeechLanguage("uz")),
            projects,
            log,
            new ProjectRepoAccess(
                new AccessPolicy(Set.of(USER)),
                new UserSettingsLookup(settingsRepository, new SpeechLanguage("uz")),
                connections,
                links,
                GitLabFixtures.integrations(api),
                Clock.systemUTC()),
            request -> {
              throw new LanguageModelException(reason, "x", null);
            },
            DEFAULT);
    long id = logged(SPOKEN);

    assertThat(failing.fromTranscript(USER, id))
        .isEqualTo(new TaskDraftResult.Failed(LanguageModelProblem.valueOf(reason.name())));
    assertThat(log.usageOf(id)).isEmpty();
  }

  @Test
  void should_report_missing_transcript_and_deny_stranger() {
    assertThat(service.fromTranscript(USER, 99)).isInstanceOf(TaskDraftResult.NotFound.class);
    assertThat(service.fromTranscript(STRANGER, logged(SPOKEN)))
        .isInstanceOf(TaskDraftResult.AccessDenied.class);
    assertThat(sent.get()).isNull();
    verifyNoInteractions(api);
  }
}
