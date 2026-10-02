package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.application.port.in.ConnectionView;
import com.alex.voicedevbot.application.port.in.TokenExpiryAlertsUseCase;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Davriy ishga tushiriladi: tugayotgan xizmat tokenlari haqida whitelist'dagi har bir userga {@code
 * 🔑 Tokenni yangilash} tugmasi bilan xabar yuboradi. Har ulanish uchun kuniga bir marta.
 */
public class TokenExpiryNotifier implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(TokenExpiryNotifier.class);

  private final TokenExpiryAlertsUseCase alerts;
  private final VoiceDevBot bot;
  private final Set<TelegramUserId> recipients;

  public TokenExpiryNotifier(
      TokenExpiryAlertsUseCase alerts, VoiceDevBot bot, Set<TelegramUserId> recipients) {
    this.alerts = Objects.requireNonNull(alerts, "alerts");
    this.bot = Objects.requireNonNull(bot, "bot");
    this.recipients = Set.copyOf(recipients);
  }

  /** Xato keyingi ishga tushirishni to'xtatmasligi uchun ushlanadi va log qilinadi. */
  @Override
  public void run() {
    try {
      for (ConnectionView view : alerts.dueAlerts()) {
        Screen alert = ConnectionScreens.tokenAlert(view);
        recipients.forEach(user -> bot.notify(user, alert));
      }
    } catch (RuntimeException e) {
      log.error("Failed to send token expiry alerts", e);
    }
  }
}
