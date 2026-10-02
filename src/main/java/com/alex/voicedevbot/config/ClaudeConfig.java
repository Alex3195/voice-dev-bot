package com.alex.voicedevbot.config;

import com.alex.voicedevbot.adapter.out.claude.ClaudeModelCatalog;
import com.alex.voicedevbot.adapter.out.claude.ClaudeTaskParser;
import com.alex.voicedevbot.adapter.out.claude.UnconfiguredClaude;
import com.alex.voicedevbot.application.port.in.ChooseModelUseCase;
import com.alex.voicedevbot.application.port.in.DraftTaskUseCase;
import com.alex.voicedevbot.application.port.out.ModelCatalog;
import com.alex.voicedevbot.application.port.out.ProjectRepository;
import com.alex.voicedevbot.application.port.out.TaskParser;
import com.alex.voicedevbot.application.port.out.TranscriptionLog;
import com.alex.voicedevbot.application.port.out.UserSettingsRepository;
import com.alex.voicedevbot.application.service.ChooseModelService;
import com.alex.voicedevbot.application.service.DraftTaskService;
import com.alex.voicedevbot.application.service.ProjectRepoAccess;
import com.alex.voicedevbot.application.service.UserSettingsLookup;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.ModelId;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Claude: transkriptdan task qoralamasi va model tanlash. Kalit bo'lmasa — o'chiq. */
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
class ClaudeConfig {

  /** Faqat kalit berilganda; aks holda Claude portlari {@link UnconfiguredClaude} bo'ladi. */
  @Bean
  @ConditionalOnExpression("!'${claude.api-key:}'.isBlank()")
  AnthropicClient anthropicClient(ClaudeProperties properties) {
    return AnthropicOkHttpClient.builder()
        .apiKey(properties.apiKey())
        .baseUrl(properties.baseUrl().toString())
        .timeout(properties.timeout())
        .build();
  }

  @Bean
  ModelId defaultModel(ClaudeProperties properties) {
    return new ModelId(properties.defaultModel());
  }

  @Bean
  DraftTaskUseCase draftTaskUseCase(
      AccessPolicy accessPolicy,
      UserSettingsLookup settings,
      ProjectRepository projects,
      TranscriptionLog log,
      ProjectRepoAccess repoAccess,
      ObjectProvider<AnthropicClient> client,
      ClaudeProperties properties,
      ModelId defaultModel) {
    AnthropicClient found = client.getIfAvailable();
    TaskParser parser =
        found == null ? new UnconfiguredClaude() : new ClaudeTaskParser(found, properties.effort());
    return new DraftTaskService(
        accessPolicy, settings, projects, log, repoAccess, parser, defaultModel);
  }

  @Bean
  ChooseModelUseCase chooseModelUseCase(
      AccessPolicy accessPolicy,
      UserSettingsLookup settings,
      UserSettingsRepository repository,
      ObjectProvider<AnthropicClient> client,
      ModelId defaultModel) {
    AnthropicClient found = client.getIfAvailable();
    ModelCatalog catalog = found == null ? new UnconfiguredClaude() : new ClaudeModelCatalog(found);
    return new ChooseModelService(accessPolicy, settings, repository, catalog, defaultModel);
  }
}
