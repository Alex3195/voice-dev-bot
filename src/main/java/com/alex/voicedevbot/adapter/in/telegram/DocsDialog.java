package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.adapter.in.telegram.BotConversation.Reply;
import com.alex.voicedevbot.application.port.in.BrowseDocsUseCase;
import com.alex.voicedevbot.application.port.in.DocsResult;
import com.alex.voicedevbot.application.port.in.RepoUnavailable;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faol project repo'sidagi hujjatlar ({@code 📄 Hujjatlar}). Tugmada hujjat yo'li o'rniga uning
 * hash'i ({@link Actions#document}) — bosilganda ro'yxatdan topiladi.
 */
public class DocsDialog {

  static final String DOCUMENT_NOT_FOUND = "Hujjat topilmadi";

  /** {@code <hash>:<sahifa>}. */
  private static final Pattern DOCUMENT = Pattern.compile("([0-9a-f]+):(\\d{1,4})");

  private final BrowseDocsUseCase docs;

  public DocsDialog(BrowseDocsUseCase docs) {
    this.docs = Objects.requireNonNull(docs, "docs");
  }

  static boolean handles(String action) {
    return action.equals(Actions.DOCS) || action.startsWith(Actions.DOC_PREFIX);
  }

  Optional<Reply> onButton(TelegramUserId user, String action) {
    if (action.equals(Actions.DOCS)) {
      return describe(docs.list(user)).map(screen -> new Reply(screen, false, ""));
    }
    Matcher matcher = DOCUMENT.matcher(action.substring(Actions.DOC_PREFIX.length()));
    return matcher.matches()
        ? open(user, matcher.group(1), Integer.parseInt(matcher.group(2)))
        : Optional.empty();
  }

  private Optional<Reply> open(TelegramUserId user, String id, int page) {
    DocsResult listed = docs.list(user);
    if (!(listed instanceof DocsResult.Listed found)) {
      return describe(listed).map(screen -> new Reply(screen, false, ""));
    }
    Optional<String> path =
        found.paths().stream().filter(candidate -> Actions.idOf(candidate).equals(id)).findFirst();
    if (path.isEmpty()) {
      return Optional.of(new Reply(DocScreens.list(found), false, DOCUMENT_NOT_FOUND));
    }
    DocsResult opened = docs.open(user, path.get());
    return opened instanceof DocsResult.Opened document
        ? Optional.of(new Reply(DocScreens.page(document, page), false, ""))
        : describe(opened).map(screen -> new Reply(screen, false, ""));
  }

  private static Optional<Screen> describe(DocsResult result) {
    return switch (result) {
      case DocsResult.Listed listed -> Optional.of(DocScreens.list(listed));
      case DocsResult.Opened opened -> Optional.of(DocScreens.page(opened, 0));
      case RepoUnavailable unavailable -> GitLabScreens.unavailable(unavailable);
    };
  }
}
