package com.alex.voicedevbot.adapter.out.claude;

import com.alex.voicedevbot.application.port.out.ModelCatalog;
import com.alex.voicedevbot.domain.ModelId;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.models.ModelCapabilities;
import com.anthropic.models.models.ModelInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Models API'dan: faqat structured outputs va {@code low} effort'ni qo'llaydigan modellar — task
 * qoralamasi shularsiz ishlamaydi.
 */
public class ClaudeModelCatalog implements ModelCatalog {

  private final AnthropicClient client;

  public ClaudeModelCatalog(AnthropicClient client) {
    this.client = Objects.requireNonNull(client, "client");
  }

  @Override
  public List<Model> models() {
    return ClaudeCalls.call(
        () -> {
          List<Model> result = new ArrayList<>();
          for (ModelInfo info : client.models().list().autoPager()) {
            if (info.capabilities().filter(ClaudeModelCatalog::suitable).isPresent()) {
              result.add(new Model(new ModelId(info.id()), info.displayName()));
            }
          }
          return result;
        });
  }

  private static boolean suitable(ModelCapabilities capabilities) {
    return capabilities.structuredOutputs().supported()
        && capabilities.effort().supported()
        && capabilities.effort().low().supported();
  }
}
