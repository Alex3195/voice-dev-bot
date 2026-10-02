package com.alex.voicedevbot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectAndGlossaryTest {

  @Test
  void should_treat_project_names_as_same_ignoring_case_and_spaces() {
    ProjectName name = new ProjectName("  ELT imzo ");

    assertThat(name.value()).isEqualTo("ELT imzo");
    assertThat(name.sameAs(new ProjectName("elt IMZO"))).isTrue();
    assertThat(name.key()).isEqualTo("elt imzo");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "  ")
  void should_reject_blank_project_name(String value) {
    assertThatThrownBy(() -> new ProjectName(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_too_long_project_name() {
    String tooLong = "a".repeat(ProjectName.MAX_LENGTH + 1);

    assertThatThrownBy(() -> new ProjectName(tooLong)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_keep_first_spelling_and_order_when_terms_repeat_in_other_case() {
    Glossary glossary = Glossary.of(List.of("Klaes", " PVX ", "klaes", "kassa"));

    assertThat(glossary.terms()).containsExactly("Klaes", "PVX", "kassa");
  }

  @Test
  void should_remove_terms_ignoring_case_and_stay_immutable() {
    Glossary original = Glossary.of(List.of("Klaes", "PVX"));

    Glossary changed = original.remove(List.of("klaes", "yo'q"));

    assertThat(changed.terms()).containsExactly("PVX");
    assertThat(original.terms()).containsExactly("Klaes", "PVX");
    assertThat(changed)
        .isEqualTo(Glossary.of(List.of("PVX")))
        .hasSameHashCodeAs(Glossary.of(List.of("PVX")));
    assertThat(changed.toString()).contains("PVX");
  }

  @Test
  void should_reject_blank_or_too_long_terms() {
    assertThatThrownBy(() -> Glossary.of(List.of(" ")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Glossary.of(List.of("a".repeat(Glossary.MAX_TERM_LENGTH + 1))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_replace_glossary_of_project() {
    ProjectName name = new ProjectName("ELT imzo");

    Project project = Project.named(name).withGlossary(Glossary.of(List.of("kassa")));

    assertThat(project).isEqualTo(new Project(name, Glossary.of(List.of("kassa"))));
    assertThat(Project.named(name).glossary().isEmpty()).isTrue();
  }
}
