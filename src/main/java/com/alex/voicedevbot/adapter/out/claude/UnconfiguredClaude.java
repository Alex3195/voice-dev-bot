package com.alex.voicedevbot.adapter.out.claude;

import com.alex.voicedevbot.application.port.out.LanguageModelException;
import com.alex.voicedevbot.application.port.out.LanguageModelException.Reason;
import com.alex.voicedevbot.application.port.out.ModelCatalog;
import com.alex.voicedevbot.application.port.out.TaskParser;
import java.util.List;

/** {@code ANTHROPIC_API_KEY} berilmagan: Claude o'chiq, bot oddiy qoralama bilan ishlaydi. */
public class UnconfiguredClaude implements TaskParser, ModelCatalog {

  @Override
  public ParsedTask parse(Request request) {
    throw notConfigured();
  }

  @Override
  public List<Model> models() {
    throw notConfigured();
  }

  private static LanguageModelException notConfigured() {
    return new LanguageModelException(Reason.NOT_CONFIGURED, "ANTHROPIC_API_KEY is not set", null);
  }
}
