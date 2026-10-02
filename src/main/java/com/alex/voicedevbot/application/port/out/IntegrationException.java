package com.alex.voicedevbot.application.port.out;

import java.util.Objects;

/** Tashqi xizmat (GitLab, GitHub) so'rovi bajarilmadi. Xabarda token bo'lmaydi. */
public class IntegrationException extends RuntimeException {

  public enum Reason {
    /** Token noto'g'ri, bekor qilingan yoki muddati o'tgan (HTTP 401). */
    UNAUTHORIZED,
    /** Tokenda ruxsat yetmaydi (HTTP 403). */
    FORBIDDEN,
    NOT_FOUND,
    /** Masalan, shu nomli repo allaqachon bor. */
    CONFLICT,
    /**
     * Token tekshiruvida: tokenda xizmat talab qiladigan ruxsat yo'q (masalan, GitHub {@code
     * repo}).
     */
    MISSING_SCOPE,
    /** Server javob bermadi yoki 5xx. */
    UNAVAILABLE
  }

  private final Reason reason;

  public IntegrationException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = Objects.requireNonNull(reason, "reason");
  }

  public Reason reason() {
    return reason;
  }
}
