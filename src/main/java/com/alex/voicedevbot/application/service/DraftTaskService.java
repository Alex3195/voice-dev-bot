package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.DraftTaskUseCase;
import com.alex.voicedevbot.application.port.in.TaskDraftResult;
import com.alex.voicedevbot.application.port.out.LanguageModelException;
import com.alex.voicedevbot.application.port.out.ProjectRepository;
import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.application.port.out.TaskParser;
import com.alex.voicedevbot.application.port.out.TaskParser.ParsedTask;
import com.alex.voicedevbot.application.port.out.TaskParser.ProjectBrief;
import com.alex.voicedevbot.application.port.out.TaskParser.RuleFile;
import com.alex.voicedevbot.application.port.out.TranscriptionLog;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.LoggedTranscript;
import com.alex.voicedevbot.domain.ModelId;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.TermCorrection;
import com.alex.voicedevbot.domain.UserSettings;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Transkript, barcha projectlar lug'ati va faol project repo'sidagi qoidalar Claude'ga beriladi.
 * Tuzatilgan transkript va chaqiruv narxi jurnalga yoziladi; yozib bo'lmasa qoralama baribir
 * qaytadi.
 */
public class DraftTaskService implements DraftTaskUseCase {

  /** Bir martada ko'pi bilan nechta lug'at taklifi. */
  static final int MAX_SUGGESTED_TERMS = 3;

  /** Repo'dan o'qiladigan qoidalar — acceptance criteria shularga moslanadi. */
  static final List<String> RULE_FILES = List.of("CLAUDE.md", ".ai/criteria.yml");

  private static final System.Logger LOG = System.getLogger(DraftTaskService.class.getName());

  private final AccessPolicy accessPolicy;
  private final UserSettingsLookup settings;
  private final ProjectRepository projects;
  private final TranscriptionLog log;
  private final ProjectRepoAccess repoAccess;
  private final TaskParser parser;
  private final ModelId defaultModel;

  public DraftTaskService(
      AccessPolicy accessPolicy,
      UserSettingsLookup settings,
      ProjectRepository projects,
      TranscriptionLog log,
      ProjectRepoAccess repoAccess,
      TaskParser parser,
      ModelId defaultModel) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.projects = Objects.requireNonNull(projects, "projects");
    this.log = Objects.requireNonNull(log, "log");
    this.repoAccess = Objects.requireNonNull(repoAccess, "repoAccess");
    this.parser = Objects.requireNonNull(parser, "parser");
    this.defaultModel = Objects.requireNonNull(defaultModel, "defaultModel");
  }

  @Override
  public TaskDraftResult fromTranscript(TelegramUserId user, long journalId) {
    if (!accessPolicy.isAllowed(user)) {
      return new TaskDraftResult.AccessDenied();
    }
    Optional<LoggedTranscript> transcript = log.find(journalId);
    if (transcript.isEmpty()) {
      return new TaskDraftResult.NotFound();
    }
    UserSettings current = settings.current(user);
    List<Project> all = projects.findAll();
    TaskParser.Request request =
        new TaskParser.Request(
            current.model().orElse(defaultModel),
            transcript.get().record().transcription().transcript().text(),
            all.stream()
                .map(project -> new ProjectBrief(project.name(), project.glossary().terms()))
                .toList(),
            current.activeProject(),
            rules(user));
    ParsedTask parsed;
    try {
      parsed = parser.parse(request);
    } catch (LanguageModelException e) {
      return new TaskDraftResult.Failed(LanguageModelProblems.of(e));
    }
    recordCorrection(journalId, parsed);
    Optional<Project> active = current.activeProject().flatMap(name -> find(all, name.value()));
    return new TaskDraftResult.Drafted(
        parsed.draft(),
        parsed
            .project()
            .flatMap(name -> find(all, name))
            .map(Project::name)
            .filter(name -> current.activeProject().filter(name::sameAs).isEmpty()),
        active
            .map(project -> suggestedTerms(project, parsed.draft().corrections()))
            .orElse(List.of()),
        parsed.correctedTranscript(),
        parsed.usage());
  }

  /** Repo ulanmagan yoki o'qib bo'lmasa — qoidasiz. */
  private List<RuleFile> rules(TelegramUserId user) {
    return repoAccess.run(
        user,
        ready -> {
          List<RuleFile> files = new ArrayList<>();
          for (String path : RULE_FILES) {
            ready
                .code()
                .readFile(ready.connection(), ready.repo(), path)
                .ifPresent(content -> files.add(new RuleFile(path, content)));
          }
          return files;
        },
        unavailable -> List.of());
  }

  @Override
  public void confirmCorrection(TelegramUserId user, long journalId) {
    if (!accessPolicy.isAllowed(user)) {
      return;
    }
    try {
      log.confirmCorrection(journalId);
    } catch (StorageException e) {
      LOG.log(Level.WARNING, "Could not confirm corrected transcript " + journalId, e);
    }
  }

  private void recordCorrection(long journalId, ParsedTask parsed) {
    try {
      log.recordCorrection(journalId, parsed.correctedTranscript(), parsed.usage());
    } catch (StorageException e) {
      LOG.log(Level.WARNING, "Could not record Claude correction for transcript " + journalId, e);
    }
  }

  private static Optional<Project> find(List<Project> all, String name) {
    String key = name.strip().toLowerCase(Locale.ROOT);
    return all.stream().filter(project -> project.name().key().equals(key)).findFirst();
  }

  /** Lug'atda hali yo'q to'g'ri atamalar. */
  private static List<String> suggestedTerms(Project project, List<TermCorrection> corrections) {
    List<String> missing =
        project.glossary().missing(corrections.stream().map(TermCorrection::correct).toList());
    return missing.subList(0, Math.min(missing.size(), MAX_SUGGESTED_TERMS));
  }
}
