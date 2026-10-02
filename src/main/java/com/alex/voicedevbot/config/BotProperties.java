package com.alex.voicedevbot.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param token Telegram bot tokeni (BotFather)
 * @param allowedUserIds botdan foydalana oladigan Telegram user ID'lar
 * @param apiUrl Telegram Bot API manzili (testlarda WireMock'ga almashtiriladi)
 * @param pollingEnabled {@code false} bo'lsa Telegram'ga ulanmaydi (testlar uchun)
 */
@Validated
@ConfigurationProperties("bot")
public record BotProperties(
    @NotBlank String token,
    @NotEmpty Set<Long> allowedUserIds,
    @NotNull @DefaultValue("https://api.telegram.org") URI apiUrl,
    @DefaultValue("true") boolean pollingEnabled) {

  @Override
  public String toString() {
    return "BotProperties[token=***, allowedUserIds="
        + allowedUserIds
        + ", apiUrl="
        + apiUrl
        + ", pollingEnabled="
        + pollingEnabled
        + "]";
  }
}
