package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.port.in.LanguageModelProblem;
import com.alex.voicedevbot.application.port.in.ModelsResult;
import java.util.ArrayList;
import java.util.List;

/** Claude modelini tanlash ekranlari va Claude xatolari matni. Holatsiz. */
final class ModelScreens {

  static final Button BACK_TO_SETTINGS = new Button("⬅️ Orqaga", Actions.SETTINGS);

  private ModelScreens() {}

  static Screen list(ModelsResult.Listed listed) {
    List<List<Button>> rows = new ArrayList<>();
    for (ModelsResult.Option option : listed.models()) {
      boolean current = option.id().equals(listed.current());
      rows.add(
          List.of(
              new Button(
                  (current ? "✅ " : "") + option.displayName(), Actions.chooseModel(option.id()))));
    }
    rows.add(List.of(BACK_TO_SETTINGS));
    return new Screen(
        "🤖 <b>Claude modeli</b>\n\nTranskriptdan task tuzishda ishlatiladi.\nHozirgi: "
            + Html.code(listed.current().value()),
        rows);
  }

  static Screen failed(LanguageModelProblem problem) {
    return new Screen(problem(problem), List.of(List.of(BACK_TO_SETTINGS)));
  }

  static String problem(LanguageModelProblem problem) {
    return switch (problem) {
      case NOT_CONFIGURED ->
          "ℹ️ Claude ulanmagan: <code>.env</code>ga <code>ANTHROPIC_API_KEY</code> qo'shing va"
              + " botni qayta ishga tushiring.";
      case UNAUTHORIZED -> "⚠️ Claude kaliti qabul qilinmadi (<code>ANTHROPIC_API_KEY</code>).";
      case MODEL_UNAVAILABLE ->
          "⚠️ Model bu so'rovni qabul qilmadi — /model bilan boshqasini tanlang.";
      case REFUSED -> "⚠️ Claude bu matndan task tuzishni rad etdi.";
      case INVALID_RESPONSE -> "⚠️ Claude javobi chala yoki noto'g'ri keldi.";
      case UNAVAILABLE -> "⚠️ Claude'ga ulanib bo'lmadi — keyinroq urinib ko'ring.";
    };
  }
}
