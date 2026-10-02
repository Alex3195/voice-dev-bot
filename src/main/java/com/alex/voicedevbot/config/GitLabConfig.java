package com.alex.voicedevbot.config;

import com.alex.voicedevbot.adapter.in.telegram.TokenExpiryNotifier;
import com.alex.voicedevbot.adapter.in.telegram.VoiceDevBot;
import com.alex.voicedevbot.adapter.out.gitlab.GitLabHttpApi;
import com.alex.voicedevbot.adapter.out.persistence.JdbcGitLabConnectionRepository;
import com.alex.voicedevbot.adapter.out.persistence.JdbcProjectRepoLinks;
import com.alex.voicedevbot.adapter.out.persistence.TokenCipher;
import com.alex.voicedevbot.adapter.out.template.ClasspathRepoTemplate;
import com.alex.voicedevbot.application.port.in.BrowseDocsUseCase;
import com.alex.voicedevbot.application.port.in.LinkRepoUseCase;
import com.alex.voicedevbot.application.port.in.ManageGitLabUseCase;
import com.alex.voicedevbot.application.port.in.ManageTasksUseCase;
import com.alex.voicedevbot.application.port.in.TokenExpiryAlertsUseCase;
import com.alex.voicedevbot.application.port.out.GitLabApi;
import com.alex.voicedevbot.application.port.out.GitLabConnectionRepository;
import com.alex.voicedevbot.application.port.out.ProjectRepoLinks;
import com.alex.voicedevbot.application.service.BrowseDocsService;
import com.alex.voicedevbot.application.service.LinkRepoService;
import com.alex.voicedevbot.application.service.ManageGitLabService;
import com.alex.voicedevbot.application.service.ManageTasksService;
import com.alex.voicedevbot.application.service.ProjectRepoAccess;
import com.alex.voicedevbot.application.service.TokenExpiryAlertsService;
import com.alex.voicedevbot.application.service.UserSettingsLookup;
import com.alex.voicedevbot.domain.AccessPolicy;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** GitLab ulanishlari, project ↔ repo, tasklar, hujjatlar va tokenlar muddatini kuzatish. */
@Configuration
@EnableConfigurationProperties(SecretsProperties.class)
class GitLabConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  /**
   * Ogohlantirish kuniga bir marta (bazada belgilanadi); tekshiruv tez-tez — sana o'tishini sezish
   * uchun.
   */
  private static final Duration ALERT_CHECK_INTERVAL = Duration.ofHours(1);

  private static final Duration ALERT_FIRST_CHECK = Duration.ofMinutes(1);

  @Bean
  TokenCipher tokenCipher(SecretsProperties properties) {
    return new TokenCipher(properties.key());
  }

  @Bean
  GitLabConnectionRepository gitLabConnectionRepository(DataSource dataSource, TokenCipher cipher) {
    return new JdbcGitLabConnectionRepository(dataSource, cipher);
  }

  @Bean
  ProjectRepoLinks projectRepoLinks(DataSource dataSource) {
    return new JdbcProjectRepoLinks(dataSource);
  }

  @Bean
  GitLabApi gitLabApi() {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    return new GitLabHttpApi(httpClient, REQUEST_TIMEOUT);
  }

  @Bean
  ManageGitLabUseCase manageGitLabUseCase(
      AccessPolicy accessPolicy,
      GitLabConnectionRepository connections,
      GitLabApi api,
      Clock clock) {
    return new ManageGitLabService(accessPolicy, connections, api, clock);
  }

  @Bean
  LinkRepoUseCase linkRepoUseCase(
      AccessPolicy accessPolicy,
      UserSettingsLookup settings,
      GitLabConnectionRepository connections,
      ProjectRepoLinks links,
      GitLabApi api,
      Clock clock) {
    return new LinkRepoService(
        accessPolicy, settings, connections, links, api, new ClasspathRepoTemplate(), clock);
  }

  @Bean
  ProjectRepoAccess projectRepoAccess(
      AccessPolicy accessPolicy,
      UserSettingsLookup settings,
      GitLabConnectionRepository connections,
      ProjectRepoLinks links,
      Clock clock) {
    return new ProjectRepoAccess(accessPolicy, settings, connections, links, clock);
  }

  @Bean
  ManageTasksUseCase manageTasksUseCase(ProjectRepoAccess access, GitLabApi api) {
    return new ManageTasksService(access, api);
  }

  @Bean
  BrowseDocsUseCase browseDocsUseCase(ProjectRepoAccess access, GitLabApi api) {
    return new BrowseDocsService(access, api);
  }

  @Bean
  TokenExpiryAlertsUseCase tokenExpiryAlertsUseCase(
      GitLabConnectionRepository connections, Clock clock) {
    return new TokenExpiryAlertsService(connections, clock);
  }

  @Bean(destroyMethod = "shutdownNow")
  @ConditionalOnProperty(name = "bot.polling-enabled", havingValue = "true", matchIfMissing = true)
  ScheduledExecutorService tokenAlertScheduler() {
    return Executors.newSingleThreadScheduledExecutor(
        runnable -> Thread.ofPlatform().name("token-expiry-alerts").daemon().unstarted(runnable));
  }

  @Bean
  @ConditionalOnProperty(name = "bot.polling-enabled", havingValue = "true", matchIfMissing = true)
  ApplicationRunner scheduleTokenAlerts(
      ScheduledExecutorService tokenAlertScheduler,
      TokenExpiryAlertsUseCase alerts,
      VoiceDevBot bot,
      BotProperties properties) {
    TokenExpiryNotifier notifier =
        new TokenExpiryNotifier(
            alerts,
            bot,
            properties.allowedUserIds().stream()
                .map(TelegramUserId::new)
                .collect(Collectors.toUnmodifiableSet()));
    return args ->
        tokenAlertScheduler.scheduleAtFixedRate(
            notifier,
            ALERT_FIRST_CHECK.toSeconds(),
            ALERT_CHECK_INTERVAL.toSeconds(),
            TimeUnit.SECONDS);
  }
}
