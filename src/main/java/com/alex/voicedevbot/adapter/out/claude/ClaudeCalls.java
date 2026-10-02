package com.alex.voicedevbot.adapter.out.claude;

import com.alex.voicedevbot.application.port.out.LanguageModelException;
import com.alex.voicedevbot.application.port.out.LanguageModelException.Reason;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.UnauthorizedException;
import java.util.Locale;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SDK xatolarini port xatosiga o'giradi va API aytgan sababni logga yozadi (botda faqat umumiy
 * sabab ko'rinadi). Xabarda kalit va so'rov matni bo'lmaydi.
 */
final class ClaudeCalls {

  private static final Logger log = LoggerFactory.getLogger(ClaudeCalls.class);

  private ClaudeCalls() {}

  static <T> T call(Supplier<T> request) {
    try {
      return request.get();
    } catch (AnthropicServiceException e) {
      log.warn("Claude returned HTTP {}: {}", e.statusCode(), e.getMessage());
      throw translate(e);
    } catch (AnthropicException e) {
      log.warn("Claude request failed: {}", e.toString());
      throw new LanguageModelException(Reason.UNAVAILABLE, "Claude is unreachable", e);
    }
  }

  private static LanguageModelException translate(AnthropicServiceException e) {
    return switch (e) {
      case UnauthorizedException ignored ->
          new LanguageModelException(Reason.UNAUTHORIZED, "Claude rejected the API key", e);
      case PermissionDeniedException ignored ->
          new LanguageModelException(Reason.UNAUTHORIZED, "Claude rejected the API key", e);
      case NotFoundException ignored -> rejected(e);
      case BadRequestException ignored when mentionsCredit(e) ->
          new LanguageModelException(Reason.NO_CREDIT, "Anthropic credit balance is too low", e);
      case BadRequestException ignored -> rejected(e);
      default ->
          new LanguageModelException(
              Reason.UNAVAILABLE, "Claude returned HTTP " + e.statusCode(), e);
    };
  }

  /** API kredit tugaganini alohida kod bilan emas, 400 va matn bilan aytadi. */
  private static boolean mentionsCredit(AnthropicServiceException e) {
    String message = e.getMessage();
    return message != null && message.toLowerCase(Locale.ROOT).contains("credit balance");
  }

  private static LanguageModelException rejected(AnthropicServiceException e) {
    return new LanguageModelException(
        Reason.MODEL_UNAVAILABLE, "Claude rejected the request: HTTP " + e.statusCode(), e);
  }
}
