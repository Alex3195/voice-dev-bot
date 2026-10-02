package com.alex.voicedevbot.application.port.in;

import java.util.List;

/** Tugayotgan yoki tugagan tokenlar haqida kunlik ogohlantirish. */
public interface TokenExpiryAlertsUseCase {

  /**
   * @return bugun hali ogohlantirilmagan, tugashiga 7 kun yoki kamroq qolgan (yoki tugagan)
   *     ulanishlar
   */
  List<ConnectionView> dueAlerts();
}
