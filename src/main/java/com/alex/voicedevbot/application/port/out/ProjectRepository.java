package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.Project;
import com.alex.voicedevbot.domain.ProjectName;
import java.util.List;
import java.util.Optional;

/** Projectlar va ularning lug'atlari. Nom bo'yicha qidiruv katta-kichik harfga qaramaydi. */
public interface ProjectRepository {

  Optional<Project> find(ProjectName name);

  /** Nomi bo'yicha alifbo tartibida. */
  List<Project> findAll();

  /** Yangi project'ni yaratadi yoki mavjudining lug'atini to'liq almashtiradi. */
  void save(Project project);
}
