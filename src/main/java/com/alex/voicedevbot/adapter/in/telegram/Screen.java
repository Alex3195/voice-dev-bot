package com.alex.voicedevbot.adapter.in.telegram;

import java.util.List;
import java.util.Objects;

/**
 * Bot ko'rsatadigan bitta "ekran": HTML matn va uning ostidagi inline tugmalar. Telegram turlaridan
 * mustaqil — test qilish oson, Telegram'ga {@link VoiceDevBot} o'giradi.
 *
 * @param html Telegram HTML (foydalanuvchi matni allaqachon escape qilingan)
 * @param rows tugmalar qatorlari; bo'sh bo'lsa klaviatura yo'q
 */
record Screen(String html, List<List<Button>> rows) {

  Screen {
    Objects.requireNonNull(html, "html");
    rows = rows.stream().map(List::copyOf).toList();
  }

  static Screen text(String html) {
    return new Screen(html, List.of());
  }

  /** Ekran tepasiga qisqa xabar qo'shadi (masalan, "✅ Project qo'shildi"). */
  Screen withNotice(String noticeHtml) {
    return new Screen(noticeHtml + "\n\n" + html, rows);
  }

  /**
   * @param action {@link Actions} dagi callback ma'lumoti (64 baytgacha)
   */
  record Button(String label, String action) {

    Button {
      Objects.requireNonNull(label, "label");
      Objects.requireNonNull(action, "action");
    }
  }
}
