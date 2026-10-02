package com.alex.voicedevbot.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param apiKey bo'sh bo'lsa Claude o'chiq — transkriptdan oddiy qoralama (birinchi gap sarlavha)
 * @param defaultModel {@code /model} bilan tanlamagan foydalanuvchi uchun
 * @param effort qoralama uchun fikrlash chuqurligi: {@code low} dan boshlanadi, namunalarda o'lchab
 *     sozlanadi
 * @param baseUrl Claude API manzili (testda — soxta server)
 */
@Validated
@ConfigurationProperties("claude")
public record ClaudeProperties(
    String apiKey,
    @NotBlank @DefaultValue("claude-opus-5-5") String defaultModel,
    @NotNull @Pattern(regexp = "low|medium|high|xhigh|max") @DefaultValue("low") String effort,
    @NotNull @DefaultValue("2m") Duration timeout,
    @NotNull @DefaultValue("https://api.anthropic.com") URI baseUrl) {}
