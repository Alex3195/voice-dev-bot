package com.alex.voicedevbot.adapter.in.telegram;

import java.util.Locale;

/**
 * Inline tugmalar callback ma'lumoti. Telegram 64 baytgacha ruxsat beradi, project nomi esa 100
 * belgigacha — shuning uchun nom/atama o'rniga uning qisqa hash'i ({@link #idOf}) yoziladi va
 * bosilganda ro'yxatdan topiladi.
 *
 * <p>{@link #NEW_MESSAGE} bilan boshlangan amal joriy xabarni tahrirlamaydi, yangi xabar yuboradi
 * (masalan, transkript ostidagi tugma — aks holda matn o'chib ketadi).
 */
final class Actions {

  static final String NEW_MESSAGE = "+";
  static final String HOME = "home";
  static final String HELP = "help";
  static final String PROJECTS = "projects";
  static final String NEW_PROJECT = "project:new";
  static final String SELECT_PROJECT = "project:";
  static final String GLOSSARY = "glossary";
  static final String ADD_TERMS = "glossary:add";
  static final String REMOVE_MODE = "glossary:remove";
  static final String REMOVE_TERM = "term:";
  static final String LANGUAGES = "lang";
  static final String SET_LANGUAGE = "lang:";
  static final String CANCEL = "cancel";

  private Actions() {}

  static String selectProject(String key) {
    return SELECT_PROJECT + idOf(key);
  }

  static String removeTerm(String term) {
    return REMOVE_TERM + idOf(term.toLowerCase(Locale.ROOT));
  }

  static String setLanguage(String code) {
    return SET_LANGUAGE + code;
  }

  static String idOf(String key) {
    return Integer.toHexString(key.hashCode());
  }
}
