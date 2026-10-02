package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.port.in.ConnectionProblem;
import com.alex.voicedevbot.application.port.in.ConnectionView;
import com.alex.voicedevbot.application.port.in.RepoLinkResult;
import com.alex.voicedevbot.application.port.in.RepoUnavailable;
import com.alex.voicedevbot.domain.Namespace;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.Provider;
import com.alex.voicedevbot.domain.Repo;
import com.alex.voicedevbot.domain.ServerAddress;
import com.alex.voicedevbot.domain.TokenInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Xizmatlarga ulanishlar va project repo'si ekranlari. Holatsiz, faqat ko'rinish. */
final class ConnectionScreens {

  static final Button BACK_TO_LIST = new Button("⬅️ Orqaga", Actions.CONNECTIONS);

  private ConnectionScreens() {}

  /**
   * @param providers bot ulana oladigan xizmatlar
   */
  static Screen list(List<ConnectionView> connections, List<Provider> providers) {
    List<List<Button>> rows = new ArrayList<>();
    for (ConnectionView view : connections) {
      rows.add(
          List.of(
              new Button(
                  statusIcon(view) + " " + icon(view) + " " + view.connection().label(),
                  Actions.CONNECTION_SHOW + view.id())));
    }
    rows.add(List.of(new Button("➕ Ulanish qo'shish", Actions.CONNECTION_ADD)));
    rows.add(List.of(new Button("⬅️ Orqaga", Actions.SETTINGS)));
    String body =
        connections.isEmpty()
            ? "Hali ulanish yo'q. "
                + providers.stream()
                    .map(Provider::displayName)
                    .collect(Collectors.joining(" yoki "))
                + " tokenini qo'shing — projectlar shu orqali repo'ga bog'lanadi."
            : connections.stream()
                .map(ConnectionScreens::summary)
                .collect(Collectors.joining("\n"));
    return new Screen(
        "🔗 <b>Ulanishlar</b>\n\n"
            + body
            + "\n\n<i>Bitta token shu serverdagi barcha projectlar uchun ishlatiladi.</i>",
        rows);
  }

  static Screen detail(ConnectionView view) {
    TokenInfo info = view.connection().info();
    String html =
        "🔗 <b>"
            + icon(view)
            + " "
            + view.connection().provider().displayName()
            + " · "
            + Html.escape(view.connection().address().label())
            + "</b>\n\n👤 @"
            + Html.escape(info.owner())
            + "\n🔑 "
            + Html.code(view.connection().token().masked())
            + " · "
            + Html.escape(String.join(", ", info.scopes().stream().sorted().toList()))
            + "\n"
            + expiry(view);
    return new Screen(
        html,
        List.of(
            List.of(
                new Button("🔑 Tokenni yangilash", Actions.CONNECTION_RENEW + view.id()),
                new Button("🗑 O'chirish", Actions.CONNECTION_REMOVE_ASK + view.id())),
            List.of(BACK_TO_LIST)));
  }

  static Screen confirmRemove(ConnectionView view) {
    return new Screen(
        "🗑 "
            + Html.bold(view.connection().label())
            + " ulanishini o'chirasizmi?\n\nUnga bog'langan projectlarning repo havolasi ham"
            + " o'chadi. "
            + view.connection().provider().displayName()
            + "dagi repo'lar o'zgarmaydi.",
        List.of(
            List.of(
                new Button("🗑 Ha, o'chirish", Actions.CONNECTION_REMOVE + view.id()),
                new Button("⬅️ Yo'q", Actions.CONNECTION_SHOW + view.id()))));
  }

  /** Har xizmatning standart serveri; o'z serverini ulash mumkin bo'lsa — alohida tugma. */
  static Screen chooseProvider(List<Provider> providers) {
    List<List<Button>> rows = new ArrayList<>();
    for (Provider provider : providers) {
      rows.add(
          List.of(
              new Button(
                  icon(provider) + " " + provider.defaultAddress().label(),
                  Actions.connectDefault(provider))));
      if (provider.selfHosted()) {
        rows.add(
            List.of(
                new Button(
                    "✍️ " + provider.displayName() + " — o'z serveri (self-hosted)",
                    Actions.connectOther(provider))));
      }
    }
    rows.add(List.of(BotScreens.CANCEL));
    return new Screen("➕ <b>Ulanish qo'shish</b>\n\nQaysi xizmatga ulanamiz?", rows);
  }

  static Screen askAddress(Provider provider) {
    return new Screen(
        "✍️ "
            + provider.displayName()
            + " server manzilini yozing.\n<i>Masalan: git.example.uz yoki"
            + " http://100.64.0.5/gitlab</i>",
        List.of(List.of(BotScreens.CANCEL)));
  }

  static Screen askToken(Provider provider, ServerAddress address) {
    return new Screen(
        "🔑 <b>"
            + icon(provider)
            + " "
            + Html.escape(address.label())
            + "</b> uchun token yuboring.\n\n"
            + tokenHowTo(provider, address),
        List.of(List.of(BotScreens.CANCEL)));
  }

  static Screen askRenewedToken(ConnectionView view) {
    return new Screen(
        "🔑 <b>"
            + Html.escape(view.connection().label())
            + "</b> uchun yangi token yuboring.\n"
            + expiry(view)
            + "\n\n"
            + tokenHowTo(view.connection().provider(), view.connection().address()),
        List.of(List.of(BotScreens.CANCEL)));
  }

  static Screen tokenAlert(ConnectionView view) {
    String title =
        switch (view.status()) {
          case EXPIRED ->
              "⛔ <b>" + view.connection().provider().displayName() + " tokeni tugagan</b>";
          case EXPIRING_SOON, ACTIVE ->
              "⚠️ <b>" + view.connection().provider().displayName() + " tokeni tugayapti</b>";
        };
    return new Screen(
        title + "\n\n" + Html.escape(view.connection().label()) + "\n" + expiry(view),
        List.of(List.of(new Button("🔑 Tokenni yangilash", Actions.CONNECTION_RENEW + view.id()))));
  }

  static String problem(ConnectionProblem problem) {
    return switch (problem) {
      case INVALID_ADDRESS ->
          "⚠️ Manzil noto'g'ri. Masalan: gitlab.com yoki https://git.example.uz";
      case INVALID_TOKEN_FORMAT ->
          "⚠️ Bu token'ga o'xshamaydi. GitLab token odatda <code>glpat-</code>, GitHub —"
              + " <code>github_pat_</code> yoki <code>ghp_</code> bilan boshlanadi.";
      case TOKEN_REJECTED -> "⚠️ Token qabul qilinmadi: noto'g'ri, bekor qilingan yoki tugagan.";
      case MISSING_SCOPE ->
          "⚠️ Token'da kerakli ruxsat yo'q (GitLab: <code>api</code>, GitHub classic:"
              + " <code>repo</code>). Yangi token yarating va ruxsatni belgilang.";
      case TOKEN_EXPIRED -> "⚠️ Bu tokenning muddati tugagan.";
      case OTHER_OWNER ->
          "⚠️ Bu token boshqa foydalanuvchiniki. Uni ➕ yangi ulanish sifatida qo'shing.";
      case UNREACHABLE -> "⚠️ Serverga ulanib bo'lmadi. Manzil va tarmoqni tekshiring.";
      case FORBIDDEN -> "⚠️ Xizmat bu amalga ruxsat bermadi (huquq yetmaydi).";
      case NOT_FOUND -> "⚠️ Topilmadi — o'chirilgan bo'lishi mumkin.";
      case INVALID_REPO_NAME ->
          "⚠️ Repo nomi noto'g'ri: faqat harf, raqam, bo'sh joy va <code>_ . -</code>, 100 belgigacha.";
      case REPO_EXISTS -> "⚠️ Bu joyda shu nomli repo allaqachon bor. Boshqa nom yozing.";
      case UNSUPPORTED -> "⚠️ Bu xizmat hali qo'llab-quvvatlanmaydi.";
    };
  }

  static Screen linked(RepoLinkResult.Linked linked) {
    Repo repo = linked.link().repo();
    String html =
        "🔗 "
            + Html.bold(linked.project().value())
            + " repo'si\n\n📂 <a href=\""
            + Html.escape(repo.webUrl().toString())
            + "\">"
            + Html.escape(repo.path())
            + "</a>\n"
            + statusIcon(linked.connection())
            + " "
            + Html.escape(linked.connection().connection().label());
    return new Screen(
        html,
        List.of(
            List.of(
                new Button("🔄 Almashtirish", Actions.REPO_CHOOSE),
                new Button("❌ Uzish", Actions.REPO_UNLINK)),
            List.of(backToCard(linked.project()))));
  }

  static Screen choose(ProjectName project, List<ConnectionView> connections) {
    List<List<Button>> rows = new ArrayList<>();
    boolean several = connections.size() > 1;
    for (ConnectionView view : connections) {
      String suffix = several ? " · " + icon(view) + " " + view.connection().label() : "";
      rows.add(
          List.of(new Button("📂 Mavjud repo'ni tanlash" + suffix, Actions.REPO_PICK + view.id())));
      rows.add(List.of(new Button("➕ Yangi repo yaratish" + suffix, Actions.REPO_NEW + view.id())));
    }
    if (connections.isEmpty()) {
      rows.add(List.of(new Button("🔗 Ulanish qo'shish", Actions.CONNECTION_ADD)));
    }
    rows.add(List.of(new Button("⏭ Keyinroq", Actions.selectProject(project.key()))));
    String body =
        connections.isEmpty()
            ? "Avval GitLab yoki GitHub tokenini qo'shing — keyin repo tanlaysiz yoki yaratasiz."
            : "Tasklar (Issue) va hujjatlar shu repo'da bo'ladi.";
    return new Screen("🔗 " + Html.bold(project.value()) + " — repo ulash\n\n" + body, rows);
  }

  static Screen repos(RepoLinkResult.Repos found) {
    List<List<Button>> rows = new ArrayList<>();
    for (Repo repo : found.repos()) {
      rows.add(
          List.of(
              new Button(
                  "📂 " + repo.path(),
                  Actions.REPO_LINK + found.connectionId() + ":" + repo.id())));
    }
    rows.add(List.of(new Button("⬅️ Orqaga", Actions.REPO_CHOOSE)));
    String title =
        found.query().isEmpty()
            ? "📂 <b>Oxirgi repo'lar</b>"
            : "🔎 " + Html.bold(found.query()) + " bo'yicha";
    String body =
        found.repos().isEmpty() ? "Hech narsa topilmadi." : "Bog'lash uchun repo'ni bosing.";
    return new Screen(
        title + "\n\n" + body + "\n<i>Boshqasini qidirish uchun nomidan bir qismini yozing.</i>",
        rows);
  }

  static Screen namespaces(RepoLinkResult.Namespaces found) {
    List<List<Button>> rows = new ArrayList<>();
    for (Namespace namespace : found.namespaces()) {
      String icon = namespace.personal() ? "👤 " : "👥 ";
      rows.add(
          List.of(
              new Button(
                  icon + namespace.path(),
                  Actions.REPO_NAMESPACE + found.connectionId() + ":" + namespace.id())));
    }
    rows.add(List.of(new Button("⬅️ Orqaga", Actions.REPO_CHOOSE)));
    return new Screen("➕ <b>Yangi repo</b>\n\nQayerda yaratamiz?", rows);
  }

  static Screen askRepoName(ProjectName project) {
    return new Screen(
        "✍️ Yangi repo nomini yozing.\n<i>Masalan: "
            + Html.escape(project.value())
            + "</i>\n\nRepo private bo'ladi; ichiga <code>CLAUDE.md</code>, <code>.ai/</code> va"
            + " <code>docs/</code> (roadmap, qarorlar, spetsifikatsiyalar) qo'yiladi.",
        List.of(List.of(BotScreens.CANCEL)));
  }

  static Screen needsNewToken(ConnectionView view) {
    return tokenAlert(view).withNotice("Amalni bajarish uchun tokenni yangilang.");
  }

  static Screen noActiveProject() {
    return new Screen(
        "🔗 Repo projectga ulanadi.\nAvval projectni tanlang yoki qo'shing.",
        List.of(
            List.of(new Button("📁 Projectlar", Actions.PROJECTS)), List.of(BotScreens.BACK_HOME)));
  }

  /**
   * Tasklar va hujjatlar uchun: repo'ga murojaat qilib bo'lmadi; whitelist'dan tashqari — bo'sh.
   */
  static Optional<Screen> unavailable(RepoUnavailable reason) {
    return switch (reason) {
      case RepoUnavailable.NoActiveProject() ->
          Optional.of(
              new Screen(
                  "📁 Tasklar va hujjatlar faol projectga tegishli.\nAvval projectni tanlang.",
                  List.of(
                      List.of(new Button("📁 Projectlar", Actions.PROJECTS)),
                      List.of(BotScreens.BACK_HOME))));
      case RepoUnavailable.NotLinked(var project) ->
          Optional.of(
              new Screen(
                  "🔗 "
                      + Html.bold(project.value())
                      + " hali repo'ga ulanmagan.\nTasklar (Issue) va hujjatlar shu repo'da"
                      + " saqlanadi.",
                  List.of(
                      List.of(new Button("🔗 Repo ulash", Actions.REPO)),
                      List.of(backToCard(project)))));
      case RepoUnavailable.NeedsNewToken(var view) -> Optional.of(needsNewToken(view));
      case RepoUnavailable.Failed(var problem) ->
          Optional.of(new Screen(problem(problem), List.of(List.of(BotScreens.BACK_HOME))));
      case RepoUnavailable.AccessDenied() -> Optional.empty();
    };
  }

  static Button repoButton(RepoLinkResult result) {
    return switch (result) {
      case RepoLinkResult.Linked linked ->
          new Button(
              statusIcon(linked.connection()) + " " + linked.link().repo().path(), Actions.REPO);
      default -> new Button("🔗 Repo ulash", Actions.REPO);
    };
  }

  static Button backToCard(ProjectName project) {
    return new Button("⬅️ Kartochka", Actions.selectProject(project.key()));
  }

  private static String summary(ConnectionView view) {
    return statusIcon(view)
        + " "
        + icon(view)
        + " "
        + Html.escape(view.connection().label())
        + " — "
        + expiry(view);
  }

  static String icon(Provider provider) {
    return switch (provider) {
      case GITLAB -> "🦊";
      case GITHUB -> "🐙";
    };
  }

  private static String icon(ConnectionView view) {
    return icon(view.connection().provider());
  }

  private static String statusIcon(ConnectionView view) {
    return switch (view.status()) {
      case ACTIVE -> "🟢";
      case EXPIRING_SOON -> "⚠️";
      case EXPIRED -> "⛔";
    };
  }

  private static String expiry(ConnectionView view) {
    String date = view.connection().info().expiresAt().map(Object::toString).orElse("muddatsiz");
    return switch (view.status()) {
      case ACTIVE -> "⏳ " + date + " gacha";
      case EXPIRING_SOON -> "⚠️ " + date + " da tugaydi";
      case EXPIRED -> "⛔ " + date + " da tugagan";
    };
  }

  /** Token yaratish sahifasiga havola; GitLab'da nom va ruxsat oldindan to'ldirilgan. */
  private static String tokenHowTo(Provider provider, ServerAddress address) {
    String steps =
        switch (provider) {
          case GITLAB -> {
            String scope = provider.requiredScope().orElseThrow();
            String url =
                address.uri()
                    + "/-/user_settings/personal_access_tokens?name=voice-dev-bot&scopes="
                    + scope;
            yield "1. <a href=\""
                + Html.escape(url)
                + "\">Token yaratish sahifasi</a> (nom va <code>"
                + scope
                + "</code> ruxsati tayyor)\n"
                + "2. Muddatini tanlang → <b>Create</b>\n";
          }
          case GITHUB ->
              "1. <a href=\"https://github.com/settings/personal-access-tokens/new\">Fine-grained"
                  + " token yaratish</a>: muddat, <b>Repository access → All repositories</b>\n"
                  + "2. <b>Permissions</b>: Contents, Issues, Pull requests — <i>Read and write</i>;"
                  + " Administration — <i>Read and write</i> (yangi repo yaratish uchun) →"
                  + " <b>Generate token</b>\n"
                  + "<i>Yoki <a href=\"https://github.com/settings/tokens/new?scopes=repo&amp;"
                  + "description=voice-dev-bot\">classic token</a> — <code>repo</code> ruxsati"
                  + " bilan.</i>\n";
        };
    return steps
        + "3. Tokenni nusxalab shu yerga yuboring\n\n"
        + "🔒 Token shifrlab saqlanadi, xabaringiz chatdan darhol o'chiriladi. Muddati tugaguncha"
        + " shu serverdagi barcha projectlar uchun ishlatiladi.";
  }
}
