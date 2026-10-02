package com.alex.voicedevbot.adapter.out.template;

import com.alex.voicedevbot.application.port.out.RepoTemplate;
import com.alex.voicedevbot.domain.ProjectName;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code src/main/resources/repo-template/} dagi fayllar; {@code {{project}}} project nomiga
 * almashtiriladi. Jar ichida papkani ro'yxatlab bo'lmagani uchun fayllar aniq sanab o'tilgan.
 */
public class ClasspathRepoTemplate implements RepoTemplate {

  static final List<String> FILES =
      List.of(
          "CLAUDE.md",
          ".ai/criteria.yml",
          ".ai/task-template.md",
          "docs/roadmap.md",
          "docs/decisions/README.md",
          "docs/specs/README.md");

  private static final String ROOT = "/repo-template/";
  private static final String PROJECT_PLACEHOLDER = "{{project}}";

  @Override
  public Map<String, String> files(ProjectName project) {
    Map<String, String> files = new LinkedHashMap<>();
    for (String path : FILES) {
      files.put(path, read(path).replace(PROJECT_PLACEHOLDER, project.value()));
    }
    return files;
  }

  private static String read(String path) {
    try (InputStream stream = ClasspathRepoTemplate.class.getResourceAsStream(ROOT + path)) {
      if (stream == null) {
        throw new IllegalStateException("Missing repo template file " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read repo template file " + path, e);
    }
  }
}
