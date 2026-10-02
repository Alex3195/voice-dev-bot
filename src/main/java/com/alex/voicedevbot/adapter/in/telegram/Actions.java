package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.domain.TaskStatus;
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
  static final String SETTINGS = "settings";
  static final String TRANSCRIPTS = "tr:";
  static final String OPEN_TRANSCRIPT = "tro:";

  static final String GITLAB = "gl";
  static final String GITLAB_PREFIX = "gl:";
  static final String GITLAB_ADD = "gl:add";
  static final String GITLAB_COM = "gl:com";
  static final String GITLAB_OTHER = "gl:url";
  static final String GITLAB_SHOW = "gl:c:";
  static final String GITLAB_RENEW = "gl:r:";
  static final String GITLAB_REMOVE_ASK = "gl:d:";
  static final String GITLAB_REMOVE = "gl:dd:";
  static final String REPO = "gl:repo";
  static final String REPO_CHOOSE = "gl:choose";
  static final String REPO_PICK = "gl:pick:";
  static final String REPO_LINK = "gl:l:";
  static final String REPO_NEW = "gl:new:";
  static final String REPO_NAMESPACE = "gl:ns:";
  static final String REPO_UNLINK = "gl:unlink";

  static final String TASKS = "tk";
  static final String TASK_PREFIX = "tk:";
  static final String TASK_GROUP = "tk:g:";
  static final String TASK_OPEN = "tk:o:";
  static final String TASK_CLOSE = "tk:c:";
  static final String TASK_REOPEN = "tk:r:";
  static final String TASK_NEW = "tk:new";
  static final String TASK_FROM_TRANSCRIPT = "tk:tr:";
  static final String TASK_EDIT_TITLE = "tk:et";
  static final String TASK_EDIT_DESCRIPTION = "tk:ed";
  static final String TASK_SKIP_DESCRIPTION = "tk:nd";
  static final String TASK_CONFIRM = "tk:ok";
  static final String TASK_REVIEW = "tk:rv";
  static final String TASK_DISCARD = "tk:x";

  static final String DOCS = "dc";
  static final String DOC_PREFIX = "dc:";

  /** {@link #transcripts} da projectsiz transkriptlar uchun project o'rnidagi belgi. */
  static final String WITHOUT_PROJECT = "-";

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

  /**
   * @param projectKey {@code null} — projectsiz transkriptlar
   */
  static String transcripts(String projectKey, int page) {
    String scope = projectKey == null ? WITHOUT_PROJECT : idOf(projectKey);
    return TRANSCRIPTS + scope + ":" + page;
  }

  static String openTranscript(long id) {
    return NEW_MESSAGE + OPEN_TRANSCRIPT + id;
  }

  /**
   * @param page 0 dan boshlanadi
   */
  static String taskGroup(TaskStatus status, int page) {
    return TASK_GROUP + status.name() + ":" + page;
  }

  static String openTask(long iid) {
    return TASK_OPEN + iid;
  }

  /** Transkript ostida — yangi xabar bo'lib chiqadi, transkript o'chmaydi. */
  static String taskFromTranscript(long journalId) {
    return NEW_MESSAGE + TASK_FROM_TRANSCRIPT + journalId;
  }

  /** Hujjat yo'li 64 baytdan uzun bo'lishi mumkin — uning hash'i yoziladi. */
  static String document(String path, int page) {
    return DOC_PREFIX + idOf(path) + ":" + page;
  }

  static String idOf(String key) {
    return Integer.toHexString(key.hashCode());
  }
}
