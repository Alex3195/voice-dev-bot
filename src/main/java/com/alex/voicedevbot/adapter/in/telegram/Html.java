package com.alex.voicedevbot.adapter.in.telegram;

/**
 * Telegram HTML parse mode uchun yordamchi. Foydalanuvchi kiritgan har qanday matn (project nomi,
 * atama, transkript) {@link #escape} orqali o'tishi shart — aks holda {@code <} yoki {@code &}
 * xabarni buzadi yoki Telegram uni rad etadi.
 */
final class Html {

  private Html() {}

  static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  static String bold(String text) {
    return "<b>" + escape(text) + "</b>";
  }

  static String code(String text) {
    return "<code>" + escape(text) + "</code>";
  }
}
