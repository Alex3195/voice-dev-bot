package com.alex.voicedevbot.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** Barcha testlar uchun bitta haqiqiy PostgreSQL (Testcontainers) — JVM davomida bir marta. */
public final class PostgresContainer {

  /** compose.yaml bilan bir xil versiya. */
  public static final String IMAGE = "postgres:18-alpine";

  private static final PostgreSQLContainer INSTANCE = new PostgreSQLContainer(IMAGE);

  static {
    INSTANCE.start();
  }

  private PostgresContainer() {}

  public static PostgreSQLContainer instance() {
    return INSTANCE;
  }

  /** {@code SpringApplication.run(...)} uchun datasource argumentlari. */
  public static String[] datasourceArgs() {
    return new String[] {
      "--spring.datasource.url=" + INSTANCE.getJdbcUrl(),
      "--spring.datasource.username=" + INSTANCE.getUsername(),
      "--spring.datasource.password=" + INSTANCE.getPassword()
    };
  }
}
