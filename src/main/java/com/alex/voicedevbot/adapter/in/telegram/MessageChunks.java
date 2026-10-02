package com.alex.voicedevbot.adapter.in.telegram;

import java.util.ArrayList;
import java.util.List;

/** Telegram bitta xabarga 4096 belgidan ko'p matn qabul qilmaydi — uzun matn bo'laklanadi. */
final class MessageChunks {

  static final int MAX_MESSAGE_LENGTH = 4096;

  private MessageChunks() {}

  /** Iloji bo'lsa so'z chegarasidan bo'ladi, so'z bo'lmasa — qat'iy limitdan. */
  static List<String> split(String text, int maxLength) {
    List<String> chunks = new ArrayList<>();
    String rest = text;
    while (rest.length() > maxLength) {
      int cut = rest.lastIndexOf(' ', maxLength);
      int end = cut > 0 ? cut : maxLength;
      chunks.add(rest.substring(0, end));
      rest = rest.substring(end).stripLeading();
    }
    if (!rest.isEmpty()) {
      chunks.add(rest);
    }
    return chunks;
  }
}
