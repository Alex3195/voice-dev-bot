package com.alex.voicedevbot.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.application.port.in.LanguageModelProblem;
import com.alex.voicedevbot.application.port.in.ModelsResult;
import com.alex.voicedevbot.application.port.out.LanguageModelException;
import com.alex.voicedevbot.application.port.out.LanguageModelException.Reason;
import com.alex.voicedevbot.application.port.out.ModelCatalog;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.ModelId;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChooseModelServiceTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ModelId OPUS = new ModelId("claude-opus-5-5");
  private static final ModelId SONNET = new ModelId("claude-sonnet-5-5");
  private static final List<ModelCatalog.Model> CATALOG =
      List.of(
          new ModelCatalog.Model(OPUS, "Claude Opus 5.5"),
          new ModelCatalog.Model(SONNET, "Claude Sonnet 5.5"));

  private final InMemoryUserSettingsRepository repository = new InMemoryUserSettingsRepository();

  private ChooseModelService service(ModelCatalog catalog) {
    return new ChooseModelService(
        new AccessPolicy(Set.of(USER)),
        new UserSettingsLookup(repository, new SpeechLanguage("uz")),
        repository,
        catalog,
        OPUS);
  }

  @Test
  void should_list_models_with_default_and_remember_choice() {
    ChooseModelService service = service(() -> CATALOG);

    ModelsResult before = service.models(USER);
    ModelsResult chosen = service.choose(USER, SONNET);

    assertThat(before)
        .isEqualTo(
            new ModelsResult.Listed(
                List.of(
                    new ModelsResult.Option(OPUS, "Claude Opus 5.5"),
                    new ModelsResult.Option(SONNET, "Claude Sonnet 5.5")),
                OPUS));
    assertThat(chosen).isEqualTo(new ModelsResult.Chosen(SONNET));
    assertThat(service.current(USER)).contains(SONNET);
    assertThat(repository.find(USER).orElseThrow().language()).isEqualTo(new SpeechLanguage("uz"));
  }

  @Test
  void should_reject_model_outside_catalog() {
    ChooseModelService service = service(() -> CATALOG);

    assertThat(service.choose(USER, new ModelId("claude-haiku-4-5")))
        .isEqualTo(new ModelsResult.Failed(LanguageModelProblem.MODEL_UNAVAILABLE));
    assertThat(service.current(USER)).contains(OPUS);
  }

  @Test
  void should_report_catalog_failure() {
    ChooseModelService service =
        service(
            () -> {
              throw new LanguageModelException(Reason.NOT_CONFIGURED, "no key", null);
            });

    assertThat(service.models(USER))
        .isEqualTo(new ModelsResult.Failed(LanguageModelProblem.NOT_CONFIGURED));
    assertThat(service.choose(USER, SONNET))
        .isEqualTo(new ModelsResult.Failed(LanguageModelProblem.NOT_CONFIGURED));
    assertThat(repository.find(USER)).isEmpty();
  }

  @Test
  void should_deny_stranger() {
    ChooseModelService service = service(() -> CATALOG);

    assertThat(service.current(STRANGER)).isEmpty();
    assertThat(service.models(STRANGER)).isInstanceOf(ModelsResult.AccessDenied.class);
    assertThat(service.choose(STRANGER, SONNET)).isInstanceOf(ModelsResult.AccessDenied.class);
    assertThat(repository.find(STRANGER)).isEmpty();
  }
}
