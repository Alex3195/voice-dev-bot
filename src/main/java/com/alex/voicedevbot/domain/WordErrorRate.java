package com.alex.voicedevbot.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * So'z xatosi foizi (WER): STT matni "to'g'ri" matndan nechta so'z bilan farq qiladi —
 * almashtirilgan, tushib qolgan va ortiqcha so'zlar, to'g'ri matndagi so'zlar soniga nisbatan.
 *
 * <p>Ikkala matn oldin {@link #words normallashtiriladi}: kichik harf, tinish belgilarsiz,
 * apostrofning barcha shakllari bitta ({@code oʻ}, {@code o’} → {@code o'}). Aks holda Claude
 * qo'ygan vergul va bosh harflar ham xato bo'lib sanalardi.
 *
 * @param referenceWords to'g'ri matndagi so'zlar soni
 * @param errors har bir farq: almashtirish, tushib qolish yoki ortiqcha so'z
 */
public record WordErrorRate(int referenceWords, List<WordError> errors) {

  private static final Pattern APOSTROPHES = Pattern.compile("[ʻʼ‘’`´ʹ]");
  private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}']+");
  private static final Pattern EDGE_APOSTROPHES = Pattern.compile("(^'+)|('+$)");

  public WordErrorRate {
    if (referenceWords < 0) {
      throw new IllegalArgumentException("Reference word count must not be negative");
    }
    errors = List.copyOf(errors);
  }

  /** Hech narsa o'lchanmagan — {@link #plus} uchun boshlang'ich qiymat. */
  public static WordErrorRate none() {
    return new WordErrorRate(0, List.of());
  }

  /**
   * Eng kam tahrir bilan so'zma-so'z tekislaydi (Levenshtein, so'zlar ustida).
   *
   * @param reference to'g'ri matn (masalan, tasdiqlangan transkript)
   * @param hypothesis STT bergan matn
   */
  public static WordErrorRate of(String reference, String hypothesis) {
    List<String> expected = words(reference);
    List<String> heard = words(hypothesis);
    int[][] cost = new int[expected.size() + 1][heard.size() + 1];
    for (int i = 0; i <= expected.size(); i++) {
      cost[i][0] = i;
    }
    for (int j = 0; j <= heard.size(); j++) {
      cost[0][j] = j;
    }
    for (int i = 1; i <= expected.size(); i++) {
      for (int j = 1; j <= heard.size(); j++) {
        int replace = cost[i - 1][j - 1] + (expected.get(i - 1).equals(heard.get(j - 1)) ? 0 : 1);
        cost[i][j] = Math.min(replace, Math.min(cost[i - 1][j], cost[i][j - 1]) + 1);
      }
    }
    return new WordErrorRate(expected.size(), backtrack(cost, expected, heard));
  }

  /** Normallashtirilgan so'zlar. */
  public static List<String> words(String text) {
    String unified = APOSTROPHES.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("'");
    return Arrays.stream(NOT_WORD.split(unified))
        .map(word -> EDGE_APOSTROPHES.matcher(word).replaceAll(""))
        .filter(word -> !word.isEmpty())
        .toList();
  }

  /** Bir nechta audio natijasi: so'zlar va xatolar qo'shiladi (uzun audio o'z ulushicha). */
  public WordErrorRate plus(WordErrorRate other) {
    List<WordError> all = new ArrayList<>(errors);
    all.addAll(other.errors);
    return new WordErrorRate(referenceWords + other.referenceWords, all);
  }

  /** 0 — mukammal; 1 dan oshishi mumkin (STT ortiqcha so'zlar qo'shsa). */
  public double rate() {
    if (referenceWords == 0) {
      return errors.isEmpty() ? 0 : 1;
    }
    return (double) errors.size() / referenceWords;
  }

  public long count(WordError.Kind kind) {
    return errors.stream().filter(error -> error.kind() == kind).count();
  }

  /**
   * Teng narxli tekislashlardan so'zlari mos keladiganini tanlaydi: avval bir xil so'z, keyin
   * tushib qolgan/ortiqcha so'z, oxirida almashtirish — shunda xatolar ro'yxatida tasodifiy
   * juftliklar ("chiqsin → endi") chiqmaydi.
   */
  private static List<WordError> backtrack(
      int[][] cost, List<String> expected, List<String> heard) {
    List<WordError> errors = new ArrayList<>();
    int i = expected.size();
    int j = heard.size();
    while (i > 0 || j > 0) {
      boolean diagonal = i > 0 && j > 0;
      if (diagonal
          && expected.get(i - 1).equals(heard.get(j - 1))
          && cost[i][j] == cost[i - 1][j - 1]) {
        i--;
        j--;
      } else if (i > 0 && cost[i][j] == cost[i - 1][j] + 1) {
        errors.add(new WordError(expected.get(i - 1), ""));
        i--;
      } else if (j > 0 && cost[i][j] == cost[i][j - 1] + 1) {
        errors.add(new WordError("", heard.get(j - 1)));
        j--;
      } else {
        errors.add(new WordError(expected.get(i - 1), heard.get(j - 1)));
        i--;
        j--;
      }
    }
    Collections.reverse(errors);
    return errors;
  }

  /**
   * Bitta farq. Tushib qolgan so'zda {@code heard} bo'sh, ortiqcha so'zda {@code expected} bo'sh.
   */
  public record WordError(String expected, String heard) {

    public enum Kind {
      SUBSTITUTION,
      DELETION,
      INSERTION
    }

    public WordError {
      Objects.requireNonNull(expected, "expected");
      Objects.requireNonNull(heard, "heard");
      if (expected.isEmpty() && heard.isEmpty()) {
        throw new IllegalArgumentException("Word error must name at least one word");
      }
    }

    public Kind kind() {
      if (heard.isEmpty()) {
        return Kind.DELETION;
      }
      return expected.isEmpty() ? Kind.INSERTION : Kind.SUBSTITUTION;
    }
  }
}
