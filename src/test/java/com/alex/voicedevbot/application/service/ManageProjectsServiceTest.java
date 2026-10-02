package com.alex.voicedevbot.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.application.port.in.ProjectCommandResult;
import com.alex.voicedevbot.application.port.in.ProjectCommandResult.ProjectSummary;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.Glossary;
import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.support.InMemoryProjectRepository;
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManageProjectsServiceTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");

  private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
  private final InMemoryUserSettingsRepository settingsRepository =
      new InMemoryUserSettingsRepository();
  private final UserSettingsLookup settings =
      new UserSettingsLookup(settingsRepository, new SpeechLanguage("uz"));
  private final ManageProjectsService service =
      new ManageProjectsService(
          new AccessPolicy(Set.of(USER)), projects, settings, settingsRepository);

  @Test
  void should_create_and_activate_project_when_it_is_new() {
    ProjectCommandResult result = service.addProject(USER, ELT_IMZO);

    assertThat(result).isEqualTo(new ProjectCommandResult.Added(ELT_IMZO));
    assertThat(projects.find(ELT_IMZO)).isPresent();
    assertThat(settings.current(USER).activeProject()).contains(ELT_IMZO);
  }

  @Test
  void should_report_existing_project_with_its_original_name_when_added_again() {
    service.addProject(USER, ELT_IMZO);

    ProjectCommandResult result = service.addProject(USER, new ProjectName("elt IMZO"));

    assertThat(result).isEqualTo(new ProjectCommandResult.AlreadyExists(ELT_IMZO));
  }

  @Test
  void should_activate_project_when_selected_by_case_insensitive_name() {
    projects.save(Project.named(ELT_IMZO));

    ProjectCommandResult result = service.selectProject(USER, new ProjectName("elt imzo"));

    assertThat(result).isEqualTo(new ProjectCommandResult.Selected(ELT_IMZO));
    assertThat(settings.current(USER).activeProject()).contains(ELT_IMZO);
  }

  @Test
  void should_not_change_active_project_when_selected_project_is_missing() {
    ProjectName missing = new ProjectName("Yo'q project");

    ProjectCommandResult result = service.selectProject(USER, missing);

    assertThat(result).isEqualTo(new ProjectCommandResult.NotFound(missing));
    assertThat(settings.current(USER).activeProject()).isEmpty();
  }

  @Test
  void should_list_projects_with_term_counts_and_active_marker() {
    projects.save(new Project(ELT_IMZO, Glossary.of(List.of("kassa", "Klaes"))));
    ProjectName finbank = new ProjectName("Finbank");
    projects.save(Project.named(finbank));
    service.selectProject(USER, ELT_IMZO);

    ProjectCommandResult result = service.listProjects(USER);

    assertThat(result)
        .isEqualTo(
            new ProjectCommandResult.Listed(
                List.of(
                    new ProjectSummary(ELT_IMZO, 2, true), new ProjectSummary(finbank, 0, false))));
  }

  @Test
  void should_deny_every_command_without_side_effects_when_user_is_not_whitelisted() {
    assertThat(service.addProject(STRANGER, ELT_IMZO))
        .isInstanceOf(ProjectCommandResult.AccessDenied.class);
    assertThat(service.selectProject(STRANGER, ELT_IMZO))
        .isInstanceOf(ProjectCommandResult.AccessDenied.class);
    assertThat(service.listProjects(STRANGER))
        .isInstanceOf(ProjectCommandResult.AccessDenied.class);
    assertThat(projects.findAll()).isEmpty();
    assertThat(settingsRepository.find(STRANGER)).isEmpty();
  }
}
