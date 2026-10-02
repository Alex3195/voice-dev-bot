package com.alex.voicedevbot.adapter.in.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.port.in.LanguageModelProblem;
import com.alex.voicedevbot.application.port.out.ModelCatalog;
import com.alex.voicedevbot.application.service.ChooseModelService;
import com.alex.voicedevbot.application.service.UserSettingsLookup;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.ModelId;
import com.alex.voicedevbot.domain.SpeechLanguage;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.support.InMemoryUserSettingsRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Claude modelini tanlash — haqiqiy use-case, soxta Models API. */
class ModelDialogTest {

  private static final TelegramUserId USER = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);
  private static final ModelId OPUS = new ModelId("claude-opus-5-5");
  private static final ModelId SONNET = new ModelId("claude-sonnet-5-5");

  private final InMemoryUserSettingsRepository repository = new InMemoryUserSettingsRepository();
  private final ModelDialog dialog =
      new ModelDialog(
          new ChooseModelService(
              new AccessPolicy(Set.of(USER)),
              new UserSettingsLookup(repository, new SpeechLanguage("uz")),
              repository,
              () ->
                  List.of(
                      new ModelCatalog.Model(OPUS, "Claude Opus 5.5"),
                      new ModelCatalog.Model(SONNET, "Claude Sonnet 5.5")),
              OPUS));

  private static List<String> labels(Screen screen) {
    return screen.rows().stream().flatMap(List::stream).map(Button::label).toList();
  }

  @Test
  void should_list_models_marking_current_and_choose_another() {
    Screen list = dialog.onButton(USER, Actions.MODELS).orElseThrow();
    Screen chosen = dialog.onButton(USER, Actions.chooseModel(SONNET)).orElseThrow();

    assertThat(list.html())
        .startsWith("🤖 <b>Claude modeli</b>")
        .contains("<code>claude-opus-5-5</code>");
    assertThat(labels(list)).containsExactly("✅ Claude Opus 5.5", "Claude Sonnet 5.5", "⬅️ Orqaga");
    assertThat(chosen.html()).startsWith("✅ Model: <code>claude-sonnet-5-5</code>");
    assertThat(labels(chosen)).contains("✅ Claude Sonnet 5.5");
    assertThat(dialog.current(USER)).contains(SONNET);
  }

  @Test
  void should_explain_unknown_or_unavailable_model() {
    Screen invalid = dialog.choose(USER, "Opus 5.5").orElseThrow();
    Screen missing = dialog.choose(USER, "claude-haiku-4-5").orElseThrow();

    assertThat(invalid.html()).startsWith("⚠️ Bunday model yo'q: <code>Opus 5.5</code>");
    assertThat(missing.html()).startsWith("⚠️ Model bu so'rovni qabul qilmadi");
    assertThat(dialog.current(USER)).contains(OPUS);
  }

  @ParameterizedTest
  @EnumSource(LanguageModelProblem.class)
  void should_describe_every_claude_problem(LanguageModelProblem problem) {
    Screen failed = ModelScreens.failed(problem);

    assertThat(failed.html()).isNotBlank();
    assertThat(labels(failed)).containsExactly("⬅️ Orqaga");
  }

  @Test
  void should_ignore_stranger() {
    assertThat(dialog.onButton(STRANGER, Actions.MODELS)).isEmpty();
    assertThat(dialog.onButton(STRANGER, Actions.chooseModel(SONNET))).isEmpty();
    assertThat(dialog.choose(STRANGER, "bad id")).isEmpty();
    assertThat(dialog.current(STRANGER)).isEmpty();
  }
}
