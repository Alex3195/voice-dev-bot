package com.alex.voicedevbot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param engine qaysi STT adapteri ishlatiladi
 * @param defaultLanguage {@code /lang} bilan til tanlamagan foydalanuvchi uchun nutq tili
 * @param whisper whisper.cpp server sozlamalari ({@code engine=whisper-cpp} bo'lganda)
 */
@Validated
@ConfigurationProperties("stt")
public record SttProperties(
    @NotNull @DefaultValue("whisper-cpp") Engine engine,
    @NotBlank @DefaultValue("uz") String defaultLanguage,
    @Valid @DefaultValue Whisper whisper) {

  public enum Engine {
    WHISPER_CPP,
    STUB
  }

  /**
   * @param url whisper-server manzili: dev'da native, serverda Docker (Tailscale orqali)
   * @param model serverda yuklangan model nomi — jurnal uchun (server uni javobda aytmaydi)
   * @param basePrompts til kodi → namuna matn; Whisper shu uslubda (masalan, lotin yozuvida) yozadi
   * @param timeout bitta audio'ni matnga aylantirish uchun maksimal vaqt (uzun fayllar minutlab
   *     ketadi)
   */
  public record Whisper(
      @NotNull @DefaultValue("http://127.0.0.1:8178") URI url,
      @NotBlank @DefaultValue("ggml-large-v3-q5_0") String model,
      Map<String, String> basePrompts,
      @NotNull @DefaultValue("15m") Duration timeout) {

    public Whisper {
      basePrompts = basePrompts == null ? Map.of() : Map.copyOf(basePrompts);
    }
  }
}
