package com.alex.voicedevbot.adapter.out.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.alex.voicedevbot.domain.ProjectName;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClasspathRepoTemplateTest {

  @Test
  void should_provide_agent_rules_and_docs_structure_with_project_name() {
    Map<String, String> files = new ClasspathRepoTemplate().files(new ProjectName("ELT imzo"));

    assertThat(files.keySet())
        .containsExactly(
            "CLAUDE.md",
            ".ai/criteria.yml",
            ".ai/task-template.md",
            "docs/roadmap.md",
            "docs/decisions/README.md",
            "docs/specs/README.md");
    assertThat(files.get("CLAUDE.md")).startsWith("# ELT imzo\n");
    assertThat(files.get("docs/roadmap.md")).startsWith("# ELT imzo — Roadmap");
    assertThat(files.values()).noneMatch(content -> content.contains("{{project}}"));
  }
}
