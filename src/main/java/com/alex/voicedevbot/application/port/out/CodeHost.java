package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.AccessToken;
import com.alex.voicedevbot.domain.MergeRequest;
import com.alex.voicedevbot.domain.Namespace;
import com.alex.voicedevbot.domain.NewTask;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.Repo;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.Task;
import com.alex.voicedevbot.domain.TokenInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GitLab server bilan ishlash. Barcha metodlar xato bo'lsa {@link IntegrationException} tashlaydi.
 */
public interface CodeHost {

  /** Token kimniki, qaysi ruxsatlari bor va qachon tugaydi. */
  TokenInfo verify(ServerAddress address, AccessToken token);

  /** Foydalanuvchi a'zo bo'lgan repo'lar, oxirgi faolligi bo'yicha. */
  List<Repo> searchRepos(ProviderConnection connection, String query);

  Repo findRepo(ProviderConnection connection, long repoId);

  /** Repo yaratish mumkin bo'lgan joylar; shaxsiy namespace birinchi. */
  List<Namespace> namespaces(ProviderConnection connection);

  /**
   * Bo'sh private repo yaratadi va boshlang'ich fayllarni bitta commit bilan qo'shadi.
   *
   * @param files repo ichidagi yo'l → matn
   */
  Repo createRepo(
      ProviderConnection connection, long namespaceId, String name, Map<String, String> files);

  /**
   * Repo'dagi barcha issue'lar (ochiq va yopiq), eng yangisi birinchi, ko'pi bilan {@code limit}.
   */
  List<Task> issues(ProviderConnection connection, long repoId, int limit);

  Task issue(ProviderConnection connection, long repoId, long iid);

  /** Issue'ni yopadigan yoki unga havola qilgan MR'lar. */
  List<MergeRequest> mergeRequests(ProviderConnection connection, long repoId, long iid);

  /**
   * @param labels yo'q bo'lsa GitLab o'zi yaratadi
   */
  Task createIssue(ProviderConnection connection, long repoId, NewTask task, List<String> labels);

  /**
   * @param open {@code true} — qayta ochish, {@code false} — yopish
   */
  Task setIssueOpen(ProviderConnection connection, long repoId, long iid, boolean open);

  /**
   * Standart branch'dagi fayllar yo'li (papkalarsiz). Papka yo'q bo'lsa — bo'sh ro'yxat.
   *
   * @param directory repo ildizidan; {@code ""} — ildiz
   * @param recursive ichki papkalar ham
   */
  List<String> files(
      ProviderConnection connection, long repoId, String directory, boolean recursive);

  /** Standart branch'dagi fayl matni; fayl yo'q bo'lsa — bo'sh. */
  Optional<String> readFile(ProviderConnection connection, long repoId, String path);
}
