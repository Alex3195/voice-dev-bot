package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.GitLabAddress;
import com.alex.voicedevbot.domain.GitLabConnection;
import com.alex.voicedevbot.domain.GitLabNamespace;
import com.alex.voicedevbot.domain.GitLabRepo;
import com.alex.voicedevbot.domain.GitLabToken;
import com.alex.voicedevbot.domain.TokenInfo;
import java.util.List;
import java.util.Map;

/** GitLab server bilan ishlash. Barcha metodlar xato bo'lsa {@link GitLabException} tashlaydi. */
public interface GitLabApi {

  /** Token kimniki, qaysi ruxsatlari bor va qachon tugaydi. */
  TokenInfo verify(GitLabAddress address, GitLabToken token);

  /** Foydalanuvchi a'zo bo'lgan repo'lar, oxirgi faolligi bo'yicha. */
  List<GitLabRepo> searchRepos(GitLabConnection connection, String query);

  GitLabRepo findRepo(GitLabConnection connection, long repoId);

  /** Repo yaratish mumkin bo'lgan joylar; shaxsiy namespace birinchi. */
  List<GitLabNamespace> namespaces(GitLabConnection connection);

  /**
   * Bo'sh private repo yaratadi va boshlang'ich fayllarni bitta commit bilan qo'shadi.
   *
   * @param files repo ichidagi yo'l → matn
   */
  GitLabRepo createRepo(
      GitLabConnection connection, long namespaceId, String name, Map<String, String> files);
}
