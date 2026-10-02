package com.alex.voicedevbot.domain;

import java.util.Objects;

/**
 * Yaratiladigan task: sarlavha va tavsif (bo'sh bo'lishi mumkin).
 *
 * @param title bo'sh emas, {@value #MAX_TITLE_LENGTH} belgigacha (GitLab chegarasi)
 */
public record NewTask(String title, String description) {

  public static final int MAX_TITLE_LENGTH = 255;

  public NewTask {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(description, "description");
    title = title.strip();
    description = description.strip();
    if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH) {
      throw new IllegalArgumentException(
          "Task title must be 1-" + MAX_TITLE_LENGTH + " characters");
    }
  }

  /**
   * Transkriptdan qoralama: birinchi gap (uzun bo'lsa qisqartirilgan) — sarlavha, butun matn —
   * tavsif.
   *
   * @param titleLength sarlavhaning eng ko'p uzunligi
   */
  public static NewTask fromText(String text, int titleLength) {
    String flat = text.strip().replaceAll("\\s+", " ");
    int sentenceEnd = firstSentenceEnd(flat);
    String sentence = sentenceEnd < 0 ? flat : flat.substring(0, sentenceEnd);
    String title =
        sentence.length() <= titleLength
            ? sentence
            : sentence.substring(0, titleLength - 1).strip() + "…";
    return new NewTask(title, text);
  }

  /** Gap oxiri — {@code . ! ?} va undan keyin bo'sh joy yoki matn oxiri ({@code v1.2} emas). */
  private static int firstSentenceEnd(String text) {
    for (int i = 1; i < text.length(); i++) {
      char c = text.charAt(i);
      boolean boundary = i + 1 == text.length() || text.charAt(i + 1) == ' ';
      if ((c == '.' || c == '!' || c == '?') && boundary) {
        return i;
      }
    }
    return -1;
  }
}
