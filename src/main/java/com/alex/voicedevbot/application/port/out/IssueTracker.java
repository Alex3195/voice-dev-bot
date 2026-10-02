package com.alex.voicedevbot.application.port.out;

import com.alex.voicedevbot.domain.NewTask;
import com.alex.voicedevbot.domain.ProviderConnection;
import com.alex.voicedevbot.domain.Task;
import java.util.List;

/**
 * Tasklar xizmati: GitLab/GitHub Issues, keyin Jira. Hozircha tasklar repo ichida ({@code repoId}).
 * Barcha metodlar xato bo'lsa {@link IntegrationException} tashlaydi.
 */
public interface IssueTracker {

  /** Barcha tasklar (ochiq va yopiq), eng yangisi birinchi, ko'pi bilan {@code limit}. */
  List<Task> issues(ProviderConnection connection, long repoId, int limit);

  Task issue(ProviderConnection connection, long repoId, long iid);

  /**
   * @param labels yo'q bo'lsa xizmat o'zi yaratadi
   */
  Task createIssue(ProviderConnection connection, long repoId, NewTask task, List<String> labels);

  /**
   * @param open {@code true} — qayta ochish, {@code false} — yopish
   */
  Task setIssueOpen(ProviderConnection connection, long repoId, long iid, boolean open);
}
