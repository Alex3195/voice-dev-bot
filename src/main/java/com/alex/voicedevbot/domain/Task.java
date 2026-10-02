package com.alex.voicedevbot.domain;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Project repo'sidagi Issue (GitLab, GitHub).
 *
 * @param iid repo ichidagi raqam ({@code #12})
 * @param description bo'sh bo'lishi mumkin
 * @param mergeRequests unga bog'langan MR'lar soni
 */
public record Task(
    long iid,
    String title,
    String description,
    boolean open,
    Optional<LocalDate> dueDate,
    int mergeRequests,
    URI webUrl) {

  public Task {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(dueDate, "dueDate");
    Objects.requireNonNull(webUrl, "webUrl");
    if (mergeRequests < 0) {
      throw new IllegalArgumentException("Merge request count must not be negative");
    }
  }

  /**
   * MR merge bo'lganini ro'yxatdan bilib bo'lmaydi — MR bilan yopilgan task bajarilgan deb
   * hisoblanadi; aniq holat {@link MergeRequest}da.
   */
  public TaskStatus status(LocalDate today) {
    if (!open) {
      return mergeRequests > 0 ? TaskStatus.DONE : TaskStatus.CLOSED;
    }
    if (dueDate.filter(today::isAfter).isPresent()) {
      return TaskStatus.OVERDUE;
    }
    return mergeRequests > 0 ? TaskStatus.IN_REVIEW : TaskStatus.OPEN;
  }
}
