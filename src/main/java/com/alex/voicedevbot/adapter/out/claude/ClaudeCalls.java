package com.alex.voicedevbot.adapter.out.claude;

import com.alex.voicedevbot.application.port.out.LanguageModelException;
import com.alex.voicedevbot.application.port.out.LanguageModelException.Reason;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.UnauthorizedException;
import java.util.function.Supplier;

/** SDK xatolarini port xatosiga o'giradi. Xabarda kalit va so'rov matni bo'lmaydi. */
final class ClaudeCalls {

  private ClaudeCalls() {}

  static <T> T call(Supplier<T> request) {
    try {
      return request.get();
    } catch (UnauthorizedException | PermissionDeniedException e) {
      throw new LanguageModelException(Reason.UNAUTHORIZED, "Claude rejected the API key", e);
    } catch (NotFoundException | BadRequestException e) {
      throw new LanguageModelException(
          Reason.MODEL_UNAVAILABLE, "Claude rejected the request: HTTP " + e.statusCode(), e);
    } catch (AnthropicServiceException e) {
      throw new LanguageModelException(
          Reason.UNAVAILABLE, "Claude returned HTTP " + e.statusCode(), e);
    } catch (AnthropicException e) {
      throw new LanguageModelException(Reason.UNAVAILABLE, "Claude is unreachable", e);
    }
  }
}
