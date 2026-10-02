package com.alex.voicedevbot.application.port.in;

/** GitLab amali nima uchun bajarilmadi — foydalanuvchiga tushunarli sabab. */
public enum GitLabProblem {
  INVALID_ADDRESS,
  INVALID_TOKEN_FORMAT,
  /** GitLab tokenni tanimadi (noto'g'ri, bekor qilingan). */
  TOKEN_REJECTED,
  MISSING_SCOPE,
  TOKEN_EXPIRED,
  /** Yangilangan token boshqa foydalanuvchiniki. */
  OTHER_OWNER,
  UNREACHABLE,
  FORBIDDEN,
  NOT_FOUND,
  INVALID_REPO_NAME,
  REPO_EXISTS
}
