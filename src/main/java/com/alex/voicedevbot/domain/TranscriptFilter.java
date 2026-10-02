package com.alex.voicedevbot.domain;

import java.util.Objects;

/** Jurnaldan qaysi transkriptlar ko'rsatiladi. */
public sealed interface TranscriptFilter {

  /** Shu project faol bo'lganda yuborilganlar. */
  record OfProject(ProjectName project) implements TranscriptFilter {

    public OfProject {
      Objects.requireNonNull(project, "project");
    }
  }

  /** Hech qaysi project faol bo'lmaganda yuborilganlar. */
  record WithoutProject() implements TranscriptFilter {}
}
