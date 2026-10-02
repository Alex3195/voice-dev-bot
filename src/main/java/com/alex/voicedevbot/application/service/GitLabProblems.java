package com.alex.voicedevbot.application.service;

import com.alex.voicedevbot.application.port.in.GitLabProblem;
import com.alex.voicedevbot.application.port.out.GitLabException;

/** Port xatosini foydalanuvchiga ko'rsatiladigan sababga o'giradi. */
final class GitLabProblems {

  private GitLabProblems() {}

  static GitLabProblem of(GitLabException e) {
    return switch (e.reason()) {
      case UNAUTHORIZED -> GitLabProblem.TOKEN_REJECTED;
      case FORBIDDEN -> GitLabProblem.FORBIDDEN;
      case NOT_FOUND -> GitLabProblem.NOT_FOUND;
      case CONFLICT -> GitLabProblem.REPO_EXISTS;
      case UNAVAILABLE -> GitLabProblem.UNREACHABLE;
    };
  }
}
