package com.alex.voicedevbot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param engine qaysi STT adapteri ishlatiladi
 * @param whisper whisper.cpp server sozlamalari ({@code engine=whisper-cpp} bo'lganda)
 */
@Validated
@ConfigurationProperties("stt")
public record SttProperties(
    @NotNull @DefaultValue("whisper-cpp") Engine engine, @Valid @DefaultValue Whisper whisper) {

  public enum Engine {
    WHISPER_CPP,
    STUB
  }

  /**
   * @param url whisper-server manzili: dev'da native, serverda Docker (Tailscale orqali)
   * @param language nutq tili (Whisper kodi)
   * @param prompt boshlang'ich matn: yozuv uslubi (lotin) va atamalar lug'ati
   * @param timeout bitta audio'ni matnga aylantirish uchun maksimal vaqt (uzun fayllar minutlab
   *     ketadi)
   */
  public record Whisper(
      @NotNull @DefaultValue("http://127.0.0.1:8178") URI url,
      @NotBlank @DefaultValue("uz") String language,
      @NotNull @DefaultValue("") String prompt,
      @NotNull @DefaultValue("15m") Duration timeout) {}
}
