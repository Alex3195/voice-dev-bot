package com.alex.voicedevbot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code ./gradlew sttBenchmark} sozlamalari.
 *
 * @param engines solishtiriladigan whisper.cpp serverlari; bo'sh bo'lsa — botning o'zi ishlatadigan
 *     {@code stt.whisper} serveri
 * @param reportDir Markdown hisobot papkasi (hisobotda transkript so'zlari bor — repo'dan
 *     tashqarida)
 */
@Validated
@ConfigurationProperties("stt.benchmark")
public record SttBenchmarkProperties(
    List<@Valid WhisperServer> engines,
    @NotNull @DefaultValue("build/reports/stt-benchmark") Path reportDir) {

  public SttBenchmarkProperties {
    engines = engines == null ? List.of() : List.copyOf(engines);
  }

  /**
   * @param url whisper-server manzili (har model o'z portida)
   * @param model serverda yuklangan model — hisobotdagi nomi
   */
  public record WhisperServer(@NotNull URI url, @NotBlank String model) {}
}
