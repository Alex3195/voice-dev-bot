package com.alex.voicedevbot.config;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param audioDir audio arxivi papkasi; serverda Docker volume yoki zaxiralanadigan disk
 */
@Validated
@ConfigurationProperties("journal")
public record JournalProperties(@NotNull @DefaultValue("data/audio") Path audioDir) {}
