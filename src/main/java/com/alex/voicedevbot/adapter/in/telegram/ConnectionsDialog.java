package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.adapter.in.telegram.BotConversation.Reply;
import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.port.in.ConnectionProblem;
import com.alex.voicedevbot.application.port.in.ConnectionResult;
import com.alex.voicedevbot.application.port.in.LinkRepoUseCase;
import com.alex.voicedevbot.application.port.in.ManageConnectionsUseCase;
import com.alex.voicedevbot.application.port.in.RepoLinkResult;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitLab ulanishlari ({@code ⚙️ → 🔗 GitLab}) va faol projectni repo'ga bog'lash. Tugmalari {@link
 * Actions#GITLAB} bilan boshlanadi; kutilayotgan kiritish (manzil, token, qidiruv, repo nomi) shu
 * yerda, xotirada.
 */
public class ConnectionsDialog {

  private static final Pattern ID = Pattern.compile("\\d{1,18}");
  private static final Pattern ID_PAIR = Pattern.compile("(\\d{1,18}):(\\d{1,18})");

  /** Bot foydalanuvchidan nimani kutyapti. */
  private sealed interface Awaiting {
    record Address() implements Awaiting {}

    record Token(ServerAddress address) implements Awaiting {}

    record RenewedToken(long connectionId) implements Awaiting {}

    record RepoQuery(long connectionId) implements Awaiting {}

    record RepoName(long connectionId, long namespaceId) implements Awaiting {}
  }

  private final ManageConnectionsUseCase gitLab;
  private final LinkRepoUseCase repos;
  private final Map<TelegramUserId, Awaiting> awaiting = new ConcurrentHashMap<>();

  public ConnectionsDialog(ManageConnectionsUseCase gitLab, LinkRepoUseCase repos) {
    this.gitLab = Objects.requireNonNull(gitLab, "gitLab");
    this.repos = Objects.requireNonNull(repos, "repos");
  }

  boolean awaitsInput(TelegramUserId user) {
    return awaiting.containsKey(user);
  }

  /** Token kutilyapti — foydalanuvchi xabari chatdan o'chirilishi kerak. */
  boolean expectsSecret(TelegramUserId user) {
    Awaiting current = awaiting.get(user);
    return current instanceof Awaiting.Token || current instanceof Awaiting.RenewedToken;
  }

  void cancel(TelegramUserId user) {
    awaiting.remove(user);
  }

  /** Project kartochkasidagi repo tugmasi; whitelist'dan tashqarida yoki project yo'q — bo'sh. */
  Optional<Button> repoButton(TelegramUserId user) {
    RepoLinkResult result = repos.show(user);
    return result instanceof RepoLinkResult.Linked || result instanceof RepoLinkResult.NotLinked
        ? Optional.of(ConnectionScreens.repoButton(result))
        : Optional.empty();
  }

  Optional<Reply> onButton(TelegramUserId user, String action) {
    awaiting.remove(user);
    return screenFor(user, action).map(screen -> new Reply(screen, false, ""));
  }

  Optional<Screen> onText(TelegramUserId user, String text) {
    Awaiting current = awaiting.remove(user);
    if (current == null) {
      return Optional.empty();
    }
    String input = text.strip();
    return switch (current) {
      case Awaiting.Address() -> askToken(user, input);
      case Awaiting.Token(var address) -> describe(gitLab.add(user, address.toString(), input));
      case Awaiting.RenewedToken(var id) -> describe(gitLab.renew(user, id, input));
      case Awaiting.RepoQuery(var id) -> searchRepos(user, id, input);
      case Awaiting.RepoName(var id, var namespace) ->
          describeRepo(repos.create(user, id, namespace, input), "✅ Repo yaratildi va ulandi");
    };
  }

  private Optional<Screen> screenFor(TelegramUserId user, String action) {
    return switch (action) {
      case Actions.GITLAB -> describe(gitLab.list(user));
      case Actions.GITLAB_ADD ->
          isAllowed(user) ? Optional.of(ConnectionScreens.chooseServer()) : Optional.empty();
      case Actions.GITLAB_COM -> askToken(user, ServerAddress.GITLAB_COM.toString());
      case Actions.GITLAB_OTHER ->
          isAllowed(user)
              ? await(user, new Awaiting.Address(), ConnectionScreens.askAddress())
              : Optional.empty();
      case Actions.REPO -> describeRepo(repos.show(user));
      case Actions.REPO_CHOOSE -> chooseRepo(user);
      case Actions.REPO_UNLINK ->
          describeRepo(repos.unlink(user)).map(screen -> screen.withNotice("❌ Repo uzildi"));
      default -> withArgument(user, action);
    };
  }

  private Optional<Screen> withArgument(TelegramUserId user, String action) {
    if (action.startsWith(Actions.GITLAB_SHOW)) {
      return id(action, Actions.GITLAB_SHOW).flatMap(id -> describe(gitLab.show(user, id)));
    }
    if (action.startsWith(Actions.GITLAB_RENEW)) {
      return id(action, Actions.GITLAB_RENEW).flatMap(id -> askRenewedToken(user, id));
    }
    if (action.startsWith(Actions.GITLAB_REMOVE_ASK)) {
      return id(action, Actions.GITLAB_REMOVE_ASK).flatMap(id -> confirmRemove(user, id));
    }
    if (action.startsWith(Actions.GITLAB_REMOVE)) {
      return id(action, Actions.GITLAB_REMOVE).flatMap(id -> describe(gitLab.remove(user, id)));
    }
    if (action.startsWith(Actions.REPO_PICK)) {
      return id(action, Actions.REPO_PICK).flatMap(id -> searchRepos(user, id, ""));
    }
    if (action.startsWith(Actions.REPO_NEW)) {
      return id(action, Actions.REPO_NEW).flatMap(id -> describeRepo(repos.namespaces(user, id)));
    }
    if (action.startsWith(Actions.REPO_LINK)) {
      return pair(action, Actions.REPO_LINK)
          .flatMap(ids -> describeRepo(repos.link(user, ids[0], ids[1]), "✅ Repo ulandi"));
    }
    if (action.startsWith(Actions.REPO_NAMESPACE)) {
      return pair(action, Actions.REPO_NAMESPACE).flatMap(ids -> askRepoName(user, ids[0], ids[1]));
    }
    return Optional.empty();
  }

  private Optional<Screen> askToken(TelegramUserId user, String address) {
    if (!isAllowed(user)) {
      return Optional.empty();
    }
    ServerAddress parsed;
    try {
      parsed = ServerAddress.parse(address);
    } catch (IllegalArgumentException e) {
      return await(
          user,
          new Awaiting.Address(),
          ConnectionScreens.askAddress()
              .withNotice(ConnectionScreens.problem(ConnectionProblem.INVALID_ADDRESS)));
    }
    return await(user, new Awaiting.Token(parsed), ConnectionScreens.askToken(parsed));
  }

  private Optional<Screen> askRenewedToken(TelegramUserId user, long id) {
    ConnectionResult result = gitLab.show(user, id);
    if (!(result instanceof ConnectionResult.Shown(var view))) {
      return describe(result);
    }
    return await(user, new Awaiting.RenewedToken(id), ConnectionScreens.askRenewedToken(view));
  }

  private Optional<Screen> confirmRemove(TelegramUserId user, long id) {
    ConnectionResult result = gitLab.show(user, id);
    return result instanceof ConnectionResult.Shown(var view)
        ? Optional.of(ConnectionScreens.confirmRemove(view))
        : describe(result);
  }

  /** Natija ko'rsatilgach, foydalanuvchi qidiruv so'zini yozishi mumkin. */
  private Optional<Screen> searchRepos(TelegramUserId user, long connectionId, String query) {
    RepoLinkResult result = repos.search(user, connectionId, query);
    if (result instanceof RepoLinkResult.Repos) {
      awaiting.put(user, new Awaiting.RepoQuery(connectionId));
    }
    return describeRepo(result);
  }

  /** Bog'langan bo'lsa ham boshqa repo tanlash uchun ulanishlar ro'yxati. */
  private Optional<Screen> chooseRepo(TelegramUserId user) {
    RepoLinkResult result = repos.show(user);
    if (result instanceof RepoLinkResult.Linked linked
        && gitLab.list(user) instanceof ConnectionResult.Listed(var connections)) {
      return Optional.of(ConnectionScreens.choose(linked.project(), connections));
    }
    return describeRepo(result);
  }

  private Optional<Screen> askRepoName(TelegramUserId user, long connectionId, long namespaceId) {
    RepoLinkResult result = repos.show(user);
    Awaiting next = new Awaiting.RepoName(connectionId, namespaceId);
    return switch (result) {
      case RepoLinkResult.Linked linked ->
          await(user, next, ConnectionScreens.askRepoName(linked.project()));
      case RepoLinkResult.NotLinked notLinked ->
          await(user, next, ConnectionScreens.askRepoName(notLinked.project()));
      default -> describeRepo(result);
    };
  }

  private static Optional<Screen> describe(ConnectionResult result) {
    return switch (result) {
      case ConnectionResult.Listed(var connections) ->
          Optional.of(ConnectionScreens.list(connections));
      case ConnectionResult.Shown(var view) -> Optional.of(ConnectionScreens.detail(view));
      case ConnectionResult.Saved(var view) ->
          Optional.of(
              ConnectionScreens.detail(view)
                  .withNotice("✅ Ulandi: " + Html.bold(view.connection().label())));
      case ConnectionResult.Removed() -> Optional.of(backToList("🗑 Ulanish o'chirildi"));
      case ConnectionResult.Rejected(var problem) ->
          Optional.of(backToList(ConnectionScreens.problem(problem)));
      case ConnectionResult.AccessDenied() -> Optional.empty();
    };
  }

  private static Optional<Screen> describeRepo(RepoLinkResult result) {
    return switch (result) {
      case RepoLinkResult.Linked linked -> Optional.of(ConnectionScreens.linked(linked));
      case RepoLinkResult.NotLinked(var project, var connections) ->
          Optional.of(ConnectionScreens.choose(project, connections));
      case RepoLinkResult.Repos found -> Optional.of(ConnectionScreens.repos(found));
      case RepoLinkResult.Namespaces found -> Optional.of(ConnectionScreens.namespaces(found));
      case RepoLinkResult.NeedsNewToken(var view) ->
          Optional.of(ConnectionScreens.needsNewToken(view));
      case RepoLinkResult.Failed(var problem) ->
          Optional.of(
              new Screen(
                  ConnectionScreens.problem(problem),
                  List.of(List.of(new Button("⬅️ Orqaga", Actions.REPO_CHOOSE)))));
      case RepoLinkResult.NoActiveProject() -> Optional.of(ConnectionScreens.noActiveProject());
      case RepoLinkResult.AccessDenied() -> Optional.empty();
    };
  }

  private static Screen backToList(String html) {
    return new Screen(html, List.of(List.of(ConnectionScreens.BACK_TO_LIST)));
  }

  /** Muvaffaqiyat xabari faqat repo haqiqatan ulangan bo'lsa (xato ekraniga emas). */
  private static Optional<Screen> describeRepo(RepoLinkResult result, String linkedNotice) {
    return describeRepo(result)
        .map(
            screen ->
                result instanceof RepoLinkResult.Linked ? screen.withNotice(linkedNotice) : screen);
  }

  private boolean isAllowed(TelegramUserId user) {
    return !(gitLab.list(user) instanceof ConnectionResult.AccessDenied);
  }

  private Optional<Screen> await(TelegramUserId user, Awaiting next, Screen question) {
    awaiting.put(user, next);
    return Optional.of(question);
  }

  private static Optional<Long> id(String action, String prefix) {
    String argument = action.substring(prefix.length());
    return ID.matcher(argument).matches()
        ? Optional.of(Long.parseLong(argument))
        : Optional.empty();
  }

  private static Optional<long[]> pair(String action, String prefix) {
    Matcher matcher = ID_PAIR.matcher(action.substring(prefix.length()));
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(
        new long[] {Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2))});
  }
}
