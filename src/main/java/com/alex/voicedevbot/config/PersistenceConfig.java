package com.alex.voicedevbot.config;

import com.alex.voicedevbot.adapter.out.persistence.JdbcProjectRepository;
import com.alex.voicedevbot.adapter.out.persistence.JdbcUserSettingsRepository;
import com.alex.voicedevbot.application.port.out.ProjectRepository;
import com.alex.voicedevbot.application.port.out.UserSettingsRepository;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** PostgreSQL repository'lari. DataSource va Flyway — Spring Boot autoconfig ({@code DB_*}). */
@Configuration
class PersistenceConfig {

  @Bean
  ProjectRepository projectRepository(DataSource dataSource) {
    return new JdbcProjectRepository(dataSource);
  }

  @Bean
  UserSettingsRepository userSettingsRepository(DataSource dataSource) {
    return new JdbcUserSettingsRepository(dataSource);
  }
}
