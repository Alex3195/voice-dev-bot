package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.LlmUsage;
import com.alex.voicedevbot.domain.ModelId;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.TaskDraft;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Transkriptni task qoralamasiga aylantiradi (Claude). Whisper xatolarini projectlar lug'ati
 * yordamida tuzatadi va task qaysi projectga tegishli ekanini aniqlaydi. Xato bo'lsa {@link
 * LanguageModelException} tashlaydi.
 */
public interface TaskParser {

  ParsedTask parse(Request request);

  /** Project nomi va lug'ati — Claude projectni va atamalarni shu bo'yicha taniydi. */
  record ProjectBrief(ProjectName name, List<String> glossary) {

    public ProjectBrief {
      Objects.requireNonNull(name, "name");
      glossary = List.copyOf(glossary);
    }
  }

  /** Project repo'sidagi qoida fayli ({@code CLAUDE.md}, {@code .ai/criteria.yml}). */
  record RuleFile(String path, String content) {

    public RuleFile {
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(content, "content");
    }
  }

  /**
   * @param rules faol project repo'sidagi qoidalar; repo ulanmagan bo'lsa — bo'sh
   */
  record Request(
      ModelId model,
      String transcript,
      List<ProjectBrief> projects,
      Optional<ProjectName> activeProject,
      List<RuleFile> rules) {

    public Request {
      Objects.requireNonNull(model, "model");
      Objects.requireNonNull(transcript, "transcript");
      Objects.requireNonNull(activeProject, "activeProject");
      projects = List.copyOf(projects);
      rules = List.copyOf(rules);
    }
  }

  /**
   * @param project Claude aniqlagan project nomi; aniq bo'lmasa — bo'sh
   */
  record ParsedTask(TaskDraft draft, Optional<String> project, LlmUsage usage) {

    public ParsedTask {
      Objects.requireNonNull(draft, "draft");
      Objects.requireNonNull(project, "project");
      Objects.requireNonNull(usage, "usage");
    }
  }
}
