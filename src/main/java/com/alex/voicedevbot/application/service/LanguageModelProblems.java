package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.LanguageModelProblem;
import com.alex.voicedevbot.application.port.out.LanguageModelException;

/** Claude porti xatosini foydalanuvchiga ko'rsatiladigan sababga o'giradi. */
final class LanguageModelProblems {

  private LanguageModelProblems() {}

  static LanguageModelProblem of(LanguageModelException e) {
    return switch (e.reason()) {
      case NOT_CONFIGURED -> LanguageModelProblem.NOT_CONFIGURED;
      case UNAUTHORIZED -> LanguageModelProblem.UNAUTHORIZED;
      case NO_CREDIT -> LanguageModelProblem.NO_CREDIT;
      case MODEL_UNAVAILABLE -> LanguageModelProblem.MODEL_UNAVAILABLE;
      case REFUSED -> LanguageModelProblem.REFUSED;
      case INVALID_RESPONSE -> LanguageModelProblem.INVALID_RESPONSE;
      case UNAVAILABLE -> LanguageModelProblem.UNAVAILABLE;
    };
  }
}
