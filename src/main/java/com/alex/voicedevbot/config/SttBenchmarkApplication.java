package com.alex.voicedevbot.config;

import com.alex.voicedevbot.adapter.in.cli.SttBenchmarkCommand;
import com.alex.voicedevbot.adapter.out.stt.WhisperCppSettings;
import com.alex.voicedevbot.adapter.out.stt.WhisperCppSpeechToText;
import com.alex.voicedevbot.application.port.out.AudioArchive;
import com.alex.voicedevbot.application.port.out.ProjectRepository;
import com.alex.voicedevbot.application.port.out.TranscriptionLog;
import com.alex.voicedevbot.application.port.out.UserSettingsRepository;
import com.alex.voicedevbot.application.service.SttBenchmarkService;
import com.alex.voicedevbot.application.service.TranscriptionHintsResolver;
import com.alex.voicedevbot.application.service.UserSettingsLookup;
import com.alex.voicedevbot.domain.SpeechLanguage;
import java.lang.System.Logger.Level;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * STT dvigatellarini o'lchash ({@code ./gradlew sttBenchmark}). Faqat baza, audio arxivi va STT
 * ko'tariladi — Telegram bot yo'q, ishlab turgan dev botga xalal bermaydi.
 *
 * <p>Ataylab {@code @Configuration} emas: botning component scan'i bu bean'larni olmasin.
 *
 * <p>Ikkinchi model bilan solishtirish:
 *
 * <pre>
 * ./gradlew sttBenchmark --args='
 *   --stt.benchmark.engines[0].url=http://127.0.0.1:8178
 *   --stt.benchmark.engines[0].model=ggml-large-v3-q5_0
 *   --stt.benchmark.engines[1].url=http://127.0.0.1:8179
 *   --stt.benchmark.engines[1].model=ggml-large-v3'
 * </pre>
 */
@EnableAutoConfiguration
@EnableConfigurationProperties({SttProperties.class, SttBenchmarkProperties.class})
@Import({PersistenceConfig.class, JournalConfig.class})
public class SttBenchmarkApplication {

  private static final System.Logger LOG =
      System.getLogger(SttBenchmarkApplication.class.getName());
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

  public static void main(String[] args) {
    try (ConfigurableApplicationContext context =
        new SpringApplicationBuilder(SttBenchmarkApplication.class)
            .web(WebApplicationType.NONE)
            .run(args)) {
      Path report = context.getBean(SttBenchmarkCommand.class).run();
      LOG.log(Level.INFO, "STT benchmark report: {0}", report.toAbsolutePath());
    }
  }

  @Bean
  SttBenchmarkCommand sttBenchmarkCommand(
      SttProperties stt,
      SttBenchmarkProperties benchmark,
      TranscriptionLog log,
      AudioArchive archive,
      ProjectRepository projects,
      UserSettingsRepository settings,
      Clock clock) {
    TranscriptionHintsResolver hints =
        new TranscriptionHintsResolver(
            new UserSettingsLookup(settings, new SpeechLanguage(stt.defaultLanguage())), projects);
    return new SttBenchmarkCommand(
        new SttBenchmarkService(log, archive, hints, engines(stt, benchmark), clock),
        benchmark.reportDir(),
        clock);
  }

  private static List<SttBenchmarkService.Engine> engines(
      SttProperties stt, SttBenchmarkProperties benchmark) {
    SttProperties.Whisper whisper = stt.whisper();
    List<SttBenchmarkProperties.WhisperServer> servers =
        benchmark.engines().isEmpty()
            ? List.of(new SttBenchmarkProperties.WhisperServer(whisper.url(), whisper.model()))
            : benchmark.engines();
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    return servers.stream()
        .map(
            server ->
                new SttBenchmarkService.Engine(
                    "whisper.cpp " + server.model(),
                    new WhisperCppSpeechToText(
                        httpClient,
                        new WhisperCppSettings(
                            server.url(),
                            server.model(),
                            whisper.basePrompts(),
                            whisper.timeout()))))
        .toList();
  }
}
