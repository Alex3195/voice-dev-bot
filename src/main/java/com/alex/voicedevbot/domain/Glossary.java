package com.alex.voicedevbot.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Project atamalari lug'ati: STT'ga xususiy nomlarni to'g'ri yozish uchun beriladi.
 *
 * <p>O'zgarmas; atamalar qo'shilgan tartibda saqlanadi, takror (katta-kichik harfga qaramay)
 * qo'shilmaydi.
 */
public final class Glossary {

  static final int MAX_TERM_LENGTH = 100;
  private static final Glossary EMPTY = new Glossary(Map.of());

  /** Kalit — kichik harfdagi atama, qiymat — foydalanuvchi yozgan ko'rinish. */
  private final Map<String, String> terms;

  private Glossary(Map<String, String> terms) {
    this.terms = terms;
  }

  public static Glossary empty() {
    return EMPTY;
  }

  public static Glossary of(Collection<String> terms) {
    return EMPTY.add(terms);
  }

  public Glossary add(Collection<String> newTerms) {
    Map<String, String> result = new LinkedHashMap<>(terms);
    for (String term : newTerms) {
      String normalized = normalize(term);
      result.putIfAbsent(keyOf(normalized), normalized);
    }
    return new Glossary(result);
  }

  public Glossary remove(Collection<String> removedTerms) {
    Map<String, String> result = new LinkedHashMap<>(terms);
    removedTerms.forEach(term -> result.remove(keyOf(normalize(term))));
    return new Glossary(result);
  }

  public List<String> terms() {
    return List.copyOf(terms.values());
  }

  public boolean isEmpty() {
    return terms.isEmpty();
  }

  private static String normalize(String term) {
    if (term == null || term.isBlank()) {
      throw new IllegalArgumentException("Glossary term must not be blank");
    }
    String stripped = term.strip();
    if (stripped.length() > MAX_TERM_LENGTH) {
      throw new IllegalArgumentException("Glossary term is longer than " + MAX_TERM_LENGTH);
    }
    return stripped;
  }

  private static String keyOf(String term) {
    return term.toLowerCase(Locale.ROOT);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Glossary that && terms().equals(that.terms());
  }

  @Override
  public int hashCode() {
    return Objects.hash(terms());
  }

  @Override
  public String toString() {
    return "Glossary" + terms();
  }
}
