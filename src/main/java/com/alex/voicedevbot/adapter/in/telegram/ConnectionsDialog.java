package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.adapter.in.telegram.BotConversation.Reply;
import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.port.in.ConnectionProblem;
import com.alex.voicedevbot.application.port.in.ConnectionResult;
import com.alex.voicedevbot.application.port.in.LinkRepoUseCase;
import com.alex.voicedevbot.application.port.in.ManageConnectionsUseCase;
import com.alex.voicedevbot.application.port.in.RepoLinkResult;
import com.alex.voicedevbot.domain.Provider;
import com.alex.voicedevbot.domain.RepoUrl;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xizmatlarga ulanishlar ({@code ⚙️ → 🔗 Ulanishlar}) va faol projectni repo'ga bog'lash. Tugmalari
 * {@link Actions#CONNECTIONS} bilan boshlanadi; kutilayotgan kiritish (manzil, token, qidiruv, repo
 * nomi) shu yerda, xotirada.
 */
public class ConnectionsDialog {

  private static final Pattern ID = Pattern.compile("\\d{1,18}");
  private static final Pattern ID_PAIR = Pattern.compile("(\\d{1,18}):(\\d{1,18})");

  /** Bot foydalanuvchidan nimani kutyapti. */
  private sealed interface Awaiting {
    record Address(Provider provider) implements Awaiting {}

    /**
     * @param repoUrl ulanish saqlangach shu havoladagi repo ulanadi
     */
    record Token(Provider provider, ServerAddress address, Optional<String> repoUrl)
        implements Awaiting {}

    record RenewedToken(long connectionId, Optional<String> repoUrl) implements Awaiting {}

    record LinkUrl() implements Awaiting {}

    record RepoQuery(long connectionId) implements Awaiting {}

    record RepoName(long connectionId, long namespaceId) implements Awaiting {}
  }

  private final ManageConnectionsUseCase manager;
  private final LinkRepoUseCase repos;
  private final Map<TelegramUserId, Awaiting> awaiting = new ConcurrentHashMap<>();

  /** Ulanish kutayotgan repo havolasi: xizmat tanlanguncha yoki token yangilanguncha. */
  private final Map<TelegramUserId, String> pendingRepoUrl = new ConcurrentHashMap<>();

  public ConnectionsDialog(ManageConnectionsUseCase manager, LinkRepoUseCase repos) {
    this.manager = Objects.requireNonNull(manager, "manager");
    this.repos = Objects.requireNonNull(repos, "repos");
  }

  static boolean handles(String action) {
    return action.equals(Actions.CONNECTIONS) || action.startsWith(Actions.CONNECTION_PREFIX);
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
    pendingRepoUrl.remove(user);
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
    Optional<String> repoUrl = Optional.ofNullable(pendingRepoUrl.remove(user));
    return screenFor(user, action, repoUrl).map(screen -> new Reply(screen, false, ""));
  }

  Optional<Screen> onText(TelegramUserId user, String text) {
    Awaiting current = awaiting.remove(user);
    if (current == null) {
      return Optional.empty();
    }
    String input = text.strip();
    return switch (current) {
      case Awaiting.Address(var provider) -> askToken(user, provider, input, Optional.empty());
      case Awaiting.Token(var provider, var address, var repoUrl) ->
          saved(user, manager.add(user, provider, address.toString(), input), repoUrl);
      case Awaiting.RenewedToken(var id, var repoUrl) ->
          saved(user, manager.renew(user, id, input), repoUrl);
      case Awaiting.LinkUrl() -> linkByUrl(user, input);
      case Awaiting.RepoQuery(var id) -> searchRepos(user, id, input);
      case Awaiting.RepoName(var id, var namespace) ->
          describeRepo(repos.create(user, id, namespace, input), "✅ Repo yaratildi va ulandi");
    };
  }

  /**
   * @param repoUrl ulanish kutayotgan havola — xizmat tanlash va token yangilash tugmalari uchun
   */
  private Optional<Screen> screenFor(TelegramUserId user, String action, Optional<String> repoUrl) {
    return switch (action) {
      case Actions.CONNECTIONS -> describe(manager.list(user));
      case Actions.CONNECTION_ADD -> chooseProvider(user);
      case Actions.REPO -> describeRepo(repos.show(user));
      case Actions.REPO_CHOOSE -> chooseRepo(user);
      case Actions.REPO_UNLINK ->
          describeRepo(repos.unlink(user)).map(screen -> screen.withNotice("❌ Repo uzildi"));
      case Actions.REPO_URL -> askRepoUrl(user);
      default -> withArgument(user, action, repoUrl);
    };
  }

  private Optional<Screen> withArgument(
      TelegramUserId user, String action, Optional<String> repoUrl) {
    if (action.startsWith(Actions.CONNECTION_DEFAULT)) {
      return provider(action, Actions.CONNECTION_DEFAULT)
          .flatMap(
              provider ->
                  askToken(user, provider, provider.defaultAddress().toString(), Optional.empty()));
    }
    if (action.startsWith(Actions.REPO_URL_PROVIDER)) {
      return provider(action, Actions.REPO_URL_PROVIDER)
          .flatMap(provider -> askTokenForUrl(user, provider, repoUrl));
    }
    if (action.startsWith(Actions.CONNECTION_OTHER)) {
      return provider(action, Actions.CONNECTION_OTHER)
          .filter(Provider::selfHosted)
          .filter(provider -> isAllowed(user))
          .flatMap(
              provider ->
                  await(
                      user,
                      new Awaiting.Address(provider),
                      ConnectionScreens.askAddress(provider)));
    }
    if (action.startsWith(Actions.CONNECTION_SHOW)) {
      return id(action, Actions.CONNECTION_SHOW).flatMap(id -> describe(manager.show(user, id)));
    }
    if (action.startsWith(Actions.CONNECTION_RENEW)) {
      return id(action, Actions.CONNECTION_RENEW).flatMap(id -> askRenewedToken(user, id, repoUrl));
    }
    if (action.startsWith(Actions.CONNECTION_REMOVE_ASK)) {
      return id(action, Actions.CONNECTION_REMOVE_ASK).flatMap(id -> confirmRemove(user, id));
    }
    if (action.startsWith(Actions.CONNECTION_REMOVE)) {
      return id(action, Actions.CONNECTION_REMOVE)
          .flatMap(id -> describe(manager.remove(user, id)));
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

  /** Faqat bot ulana oladigan xizmatlar taklif qilinadi. */
  private Optional<Screen> chooseProvider(TelegramUserId user) {
    return manager.list(user) instanceof ConnectionResult.Listed listed
        ? Optional.of(ConnectionScreens.chooseProvider(listed.providers()))
        : Optional.empty();
  }

  private Optional<Screen> askRepoUrl(TelegramUserId user) {
    RepoLinkResult result = repos.show(user);
    return result instanceof RepoLinkResult.Linked || result instanceof RepoLinkResult.NotLinked
        ? await(user, new Awaiting.LinkUrl(), ConnectionScreens.askRepoUrl())
        : describeRepo(result);
  }

  /**
   * Ulanish yo'q bo'lsa: bulutdagi server — darhol token so'raladi, notanish server — avval xizmat.
   * Token tugagan bo'lsa yangilash taklif qilinadi; ikkala holda ham havola eslab qolinadi.
   */
  private Optional<Screen> linkByUrl(TelegramUserId user, String url) {
    RepoLinkResult result = repos.linkByUrl(user, url);
    switch (result) {
      case RepoLinkResult.NeedsConnection(var address, var providers)
          when providers.size() == 1 && providers.getFirst().defaultAddress().equals(address) -> {
        Provider provider = providers.getFirst();
        return await(
            user,
            new Awaiting.Token(provider, address, Optional.of(url)),
            ConnectionScreens.askToken(provider, address)
                .withNotice(ConnectionScreens.noConnection(address)));
      }
      case RepoLinkResult.NeedsConnection unused -> pendingRepoUrl.put(user, url);
      case RepoLinkResult.NeedsNewToken unused -> pendingRepoUrl.put(user, url);
      case RepoLinkResult.Failed(var problem)
          when problem == ConnectionProblem.INVALID_REPO_URL -> {
        return await(
            user,
            new Awaiting.LinkUrl(),
            ConnectionScreens.askRepoUrl().withNotice(ConnectionScreens.problem(problem)));
      }
      default -> {
        // natija o'zi ko'rsatiladi
      }
    }
    return describeRepo(result, "✅ Repo ulandi");
  }

  /** Notanish server uchun tanlangan xizmat: havoladagi serverga token so'raladi. */
  private Optional<Screen> askTokenForUrl(
      TelegramUserId user, Provider provider, Optional<String> repoUrl) {
    if (repoUrl.isEmpty()) {
      return describeRepo(repos.show(user));
    }
    ServerAddress address = RepoUrl.parse(repoUrl.get()).server();
    return askToken(user, provider, address.toString(), repoUrl);
  }

  /** Ulanish saqlangach, kutilayotgan havola bo'lsa — repo ham ulanadi. */
  private Optional<Screen> saved(
      TelegramUserId user, ConnectionResult result, Optional<String> repoUrl) {
    if (result instanceof ConnectionResult.Saved && repoUrl.isPresent()) {
      return linkByUrl(user, repoUrl.get());
    }
    return describe(result);
  }

  private Optional<Screen> askToken(
      TelegramUserId user, Provider provider, String address, Optional<String> repoUrl) {
    if (!isAllowed(user)) {
      return Optional.empty();
    }
    ServerAddress parsed;
    try {
      parsed = ServerAddress.parse(address);
    } catch (IllegalArgumentException e) {
      return await(
          user,
          new Awaiting.Address(provider),
          ConnectionScreens.askAddress(provider)
              .withNotice(ConnectionScreens.problem(ConnectionProblem.INVALID_ADDRESS)));
    }
    return await(
        user,
        new Awaiting.Token(provider, parsed, repoUrl),
        ConnectionScreens.askToken(provider, parsed));
  }

  private Optional<Screen> askRenewedToken(TelegramUserId user, long id, Optional<String> repoUrl) {
    ConnectionResult result = manager.show(user, id);
    if (!(result instanceof ConnectionResult.Shown(var view))) {
      return describe(result);
    }
    return await(
        user, new Awaiting.RenewedToken(id, repoUrl), ConnectionScreens.askRenewedToken(view));
  }

  private Optional<Screen> confirmRemove(TelegramUserId user, long id) {
    ConnectionResult result = manager.show(user, id);
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
        && manager.list(user) instanceof ConnectionResult.Listed listed) {
      return Optional.of(ConnectionScreens.choose(linked.project(), listed.connections()));
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
      case ConnectionResult.Listed(var connections, var providers) ->
          Optional.of(ConnectionScreens.list(connections, providers));
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
      case RepoLinkResult.NeedsConnection(var address, var providers) ->
          Optional.of(ConnectionScreens.chooseServerProvider(address, providers));
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
    return !(manager.list(user) instanceof ConnectionResult.AccessDenied);
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

  private static Optional<Provider> provider(String action, String prefix) {
    String name = action.substring(prefix.length());
    return Arrays.stream(Provider.values())
        .filter(provider -> provider.name().equals(name))
        .findFirst();
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
