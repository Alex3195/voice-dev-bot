package com.alex.voicedevbot.domain;

import java.util.Objects;

/** Bot ishlaydigan project va uning atamalar lug'ati. */
public record Project(ProjectName name, Glossary glossary) {

  public Project {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(glossary, "glossary");
  }

  public static Project named(ProjectName name) {
    return new Project(name, Glossary.empty());
  }

  public Project withGlossary(Glossary newGlossary) {
    return new Project(name, newGlossary);
  }
}
