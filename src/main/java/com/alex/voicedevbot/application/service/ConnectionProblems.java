package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.ConnectionProblem;
import com.alex.voicedevbot.application.port.out.IntegrationException;

/** Port xatosini foydalanuvchiga ko'rsatiladigan sababga o'giradi. */
final class ConnectionProblems {

  private ConnectionProblems() {}

  static ConnectionProblem of(IntegrationException e) {
    return switch (e.reason()) {
      case UNAUTHORIZED -> ConnectionProblem.TOKEN_REJECTED;
      case FORBIDDEN -> ConnectionProblem.FORBIDDEN;
      case NOT_FOUND -> ConnectionProblem.NOT_FOUND;
      case CONFLICT -> ConnectionProblem.REPO_EXISTS;
      case UNAVAILABLE -> ConnectionProblem.UNREACHABLE;
    };
  }
}
