package com.alex.voicedevbot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param key xizmat (GitLab, GitHub) tokenlarini shifrlash kaliti: 32 bayt Base64 ({@code openssl
 *     rand -base64 32}). Almashtirilsa saqlangan tokenlarni o'qib bo'lmaydi.
 */
@Validated
@ConfigurationProperties("secrets")
public record SecretsProperties(@NotBlank String key) {

  @Override
  public String toString() {
    return "SecretsProperties[key=***]";
  }
}
