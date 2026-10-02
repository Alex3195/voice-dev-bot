package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.ProjectName;
import java.util.Map;

/** Yangi repo'ga qo'yiladigan boshlang'ich fayllar: agent qoidalari va hujjatlar tuzilmasi. */
public interface RepoTemplate {

  /**
   * @return repo ichidagi yo'l → matn
   */
  Map<String, String> files(ProjectName project);
}
