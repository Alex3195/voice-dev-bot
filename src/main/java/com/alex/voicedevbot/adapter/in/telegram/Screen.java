package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.domain.AudioKind;
import java.util.List;
import java.util.Objects;

/**
 * Bot ko'rsatadigan bitta "ekran": HTML matn va uning ostidagi inline tugmalar. Telegram turlaridan
 * mustaqil — test qilish oson, Telegram'ga {@link VoiceDevBot} o'giradi.
 *
 * @param html Telegram HTML (foydalanuvchi matni allaqachon escape qilingan)
 * @param rows tugmalar qatorlari; bo'sh bo'lsa klaviatura yo'q
 * @param attachments matndan oldin yuboriladigan audio'lar (masalan, jurnaldagi transkript
 *     audio'si)
 */
record Screen(String html, List<List<Button>> rows, List<Attachment> attachments) {

  Screen {
    Objects.requireNonNull(html, "html");
    rows = rows.stream().map(List::copyOf).toList();
    attachments = List.copyOf(attachments);
  }

  Screen(String html, List<List<Button>> rows) {
    this(html, rows, List.of());
  }

  static Screen text(String html) {
    return new Screen(html, List.of());
  }

  /** Ekran tepasiga qisqa xabar qo'shadi (masalan, "✅ Project qo'shildi"). */
  Screen withNotice(String noticeHtml) {
    return new Screen(noticeHtml + "\n\n" + html, rows, attachments);
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

  /**
   * Telegram'da allaqachon bor fayl — qayta yuklanmaydi, {@code file_id} bo'yicha yuboriladi.
   *
   * @param kind qaysi ko'rinishda yuborish (voice, video xabar va h.k.)
   */
  record Attachment(AudioKind kind, String fileId) {

    Attachment {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(fileId, "fileId");
    }
  }
}
