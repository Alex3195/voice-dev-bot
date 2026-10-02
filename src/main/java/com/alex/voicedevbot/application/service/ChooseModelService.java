package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.ChooseModelUseCase;
import com.alex.voicedevbot.application.port.in.LanguageModelProblem;
import com.alex.voicedevbot.application.port.in.ModelsResult;
import com.alex.voicedevbot.application.port.out.LanguageModelException;
import com.alex.voicedevbot.application.port.out.ModelCatalog;
import com.alex.voicedevbot.application.port.out.UserSettingsRepository;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.ModelId;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ChooseModelService implements ChooseModelUseCase {

  private final AccessPolicy accessPolicy;
  private final UserSettingsLookup settings;
  private final UserSettingsRepository repository;
  private final ModelCatalog catalog;
  private final ModelId defaultModel;

  public ChooseModelService(
      AccessPolicy accessPolicy,
      UserSettingsLookup settings,
      UserSettingsRepository repository,
      ModelCatalog catalog,
      ModelId defaultModel) {
    this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.repository = Objects.requireNonNull(repository, "repository");
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.defaultModel = Objects.requireNonNull(defaultModel, "defaultModel");
  }

  @Override
  public Optional<ModelId> current(TelegramUserId user) {
    return accessPolicy.isAllowed(user)
        ? Optional.of(settings.current(user).model().orElse(defaultModel))
        : Optional.empty();
  }

  @Override
  public ModelsResult models(TelegramUserId user) {
    if (!accessPolicy.isAllowed(user)) {
      return new ModelsResult.AccessDenied();
    }
    try {
      List<ModelsResult.Option> options =
          catalog.models().stream()
              .map(model -> new ModelsResult.Option(model.id(), model.displayName()))
              .toList();
      return new ModelsResult.Listed(options, current(user).orElseThrow());
    } catch (LanguageModelException e) {
      return new ModelsResult.Failed(LanguageModelProblems.of(e));
    }
  }

  @Override
  public ModelsResult choose(TelegramUserId user, ModelId model) {
    if (!(models(user) instanceof ModelsResult.Listed listed)) {
      return models(user);
    }
    if (listed.models().stream().noneMatch(option -> option.id().equals(model))) {
      return new ModelsResult.Failed(LanguageModelProblem.MODEL_UNAVAILABLE);
    }
    repository.save(settings.current(user).withModel(model));
    return new ModelsResult.Chosen(model);
  }
}
