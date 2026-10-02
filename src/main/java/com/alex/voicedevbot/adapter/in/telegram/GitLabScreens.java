package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.port.in.ConnectionView;
import com.alex.voicedevbot.application.port.in.GitLabProblem;
import com.alex.voicedevbot.application.port.in.RepoLinkResult;
import com.alex.voicedevbot.application.port.in.RepoUnavailable;
import com.alex.voicedevbot.domain.GitLabAddress;
import com.alex.voicedevbot.domain.GitLabNamespace;
import com.alex.voicedevbot.domain.GitLabRepo;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.TokenInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** GitLab ulanishlari va project repo'si ekranlari. Holatsiz, faqat ko'rinish. */
final class GitLabScreens {

  static final Button BACK_TO_LIST = new Button("⬅️ Orqaga", Actions.GITLAB);

  private GitLabScreens() {}

  static Screen list(List<ConnectionView> connections) {
    List<List<Button>> rows = new ArrayList<>();
    for (ConnectionView view : connections) {
      rows.add(
          List.of(
              new Button(
                  statusIcon(view) + " " + view.connection().label(),
                  Actions.GITLAB_SHOW + view.id())));
    }
    rows.add(List.of(new Button("➕ Ulanish qo'shish", Actions.GITLAB_ADD)));
    rows.add(List.of(new Button("⬅️ Orqaga", Actions.SETTINGS)));
    String body =
        connections.isEmpty()
            ? "Hali ulanish yo'q. gitlab.com yoki o'z serveringizni ulang — projectlar shu orqali"
                + " repo'ga bog'lanadi."
            : connections.stream().map(GitLabScreens::summary).collect(Collectors.joining("\n"));
    return new Screen("🔗 <b>GitLab ulanishlari</b>\n\n" + body, rows);
  }

  static Screen detail(ConnectionView view) {
    TokenInfo info = view.connection().info();
    String html =
        "🔗 <b>"
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
                new Button("🔑 Tokenni yangilash", Actions.GITLAB_RENEW + view.id()),
                new Button("🗑 O'chirish", Actions.GITLAB_REMOVE_ASK + view.id())),
            List.of(BACK_TO_LIST)));
  }

  static Screen confirmRemove(ConnectionView view) {
    return new Screen(
        "🗑 "
            + Html.bold(view.connection().label())
            + " ulanishini o'chirasizmi?\n\nUnga bog'langan projectlarning repo havolasi ham"
            + " o'chadi. GitLab'dagi repo'lar o'zgarmaydi.",
        List.of(
            List.of(
                new Button("🗑 Ha, o'chirish", Actions.GITLAB_REMOVE + view.id()),
                new Button("⬅️ Yo'q", Actions.GITLAB_SHOW + view.id()))));
  }

  static Screen chooseServer() {
    return new Screen(
        "➕ <b>GitLab ulash</b>\n\nQaysi serverga ulanamiz?",
        List.of(
            List.of(new Button("🦊 gitlab.com", Actions.GITLAB_COM)),
            List.of(new Button("✍️ Boshqa manzil (self-hosted)", Actions.GITLAB_OTHER)),
            List.of(BotScreens.CANCEL)));
  }

  static Screen askAddress() {
    return new Screen(
        "✍️ GitLab server manzilini yozing.\n<i>Masalan: git.example.uz yoki"
            + " http://100.64.0.5/gitlab</i>",
        List.of(List.of(BotScreens.CANCEL)));
  }

  static Screen askToken(GitLabAddress address) {
    return new Screen(
        "🔑 <b>"
            + Html.escape(address.label())
            + "</b> uchun token yuboring.\n\n"
            + tokenHowTo(address),
        List.of(List.of(BotScreens.CANCEL)));
  }

  static Screen askRenewedToken(ConnectionView view) {
    return new Screen(
        "🔑 <b>"
            + Html.escape(view.connection().label())
            + "</b> uchun yangi token yuboring.\n"
            + expiry(view)
            + "\n\n"
            + tokenHowTo(view.connection().address()),
        List.of(List.of(BotScreens.CANCEL)));
  }

  static Screen tokenAlert(ConnectionView view) {
    String title =
        switch (view.status()) {
          case EXPIRED -> "⛔ <b>GitLab tokeni tugagan</b>";
          case EXPIRING_SOON, ACTIVE -> "⚠️ <b>GitLab tokeni tugayapti</b>";
        };
    return new Screen(
        title + "\n\n" + Html.escape(view.connection().label()) + "\n" + expiry(view),
        List.of(List.of(new Button("🔑 Tokenni yangilash", Actions.GITLAB_RENEW + view.id()))));
  }

  static String problem(GitLabProblem problem) {
    return switch (problem) {
      case INVALID_ADDRESS ->
          "⚠️ Manzil noto'g'ri. Masalan: gitlab.com yoki https://git.example.uz";
      case INVALID_TOKEN_FORMAT ->
          "⚠️ Bu token'ga o'xshamaydi. GitLab token odatda glpat- bilan boshlanadi.";
      case TOKEN_REJECTED ->
          "⚠️ GitLab tokenni qabul qilmadi: noto'g'ri, bekor qilingan yoki tugagan.";
      case MISSING_SCOPE ->
          "⚠️ Token'da <code>api</code> ruxsati yo'q. Yangi token yarating va <code>api</code>ni"
              + " belgilang.";
      case TOKEN_EXPIRED -> "⚠️ Bu tokenning muddati tugagan.";
      case OTHER_OWNER ->
          "⚠️ Bu token boshqa GitLab foydalanuvchisiniki. Uni ➕ yangi ulanish sifatida qo'shing.";
      case UNREACHABLE -> "⚠️ GitLab serveriga ulanib bo'lmadi. Manzil va tarmoqni tekshiring.";
      case FORBIDDEN -> "⚠️ GitLab bu amalga ruxsat bermadi (huquq yetmaydi).";
      case NOT_FOUND -> "⚠️ Topilmadi — o'chirilgan bo'lishi mumkin.";
      case INVALID_REPO_NAME ->
          "⚠️ Repo nomi noto'g'ri: faqat harf, raqam, bo'sh joy va <code>_ . -</code>, 100 belgigacha.";
      case REPO_EXISTS -> "⚠️ Bu joyda shu nomli repo allaqachon bor. Boshqa nom yozing.";
    };
  }

  static Screen linked(RepoLinkResult.Linked linked) {
    GitLabRepo repo = linked.link().repo();
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
      String suffix = several ? " · " + view.connection().address().label() : "";
      rows.add(
          List.of(new Button("📂 Mavjud repo'ni tanlash" + suffix, Actions.REPO_PICK + view.id())));
      rows.add(List.of(new Button("➕ Yangi repo yaratish" + suffix, Actions.REPO_NEW + view.id())));
    }
    if (connections.isEmpty()) {
      rows.add(List.of(new Button("🔗 GitLab ulash", Actions.GITLAB_ADD)));
    }
    rows.add(List.of(new Button("⏭ Keyinroq", Actions.selectProject(project.key()))));
    String body =
        connections.isEmpty()
            ? "Avval GitLab ulang — keyin repo tanlaysiz yoki yaratasiz."
            : "Tasklar (GitLab Issue) va hujjatlar shu repo'da bo'ladi.";
    return new Screen("🔗 " + Html.bold(project.value()) + " — repo ulash\n\n" + body, rows);
  }

  static Screen repos(RepoLinkResult.Repos found) {
    List<List<Button>> rows = new ArrayList<>();
    for (GitLabRepo repo : found.repos()) {
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
    for (GitLabNamespace namespace : found.namespaces()) {
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
                      + " hali GitLab repo'ga ulanmagan.\nTasklar (Issue) va hujjatlar shu repo'da"
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
    return statusIcon(view) + " " + Html.escape(view.connection().label()) + " — " + expiry(view);
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

  /** GitLab token yaratish sahifasiga nom va {@code api} ruxsati oldindan to'ldirilgan havola. */
  private static String tokenHowTo(GitLabAddress address) {
    String url =
        address.uri()
            + "/-/user_settings/personal_access_tokens?name=voice-dev-bot&scopes="
            + TokenInfo.REQUIRED_SCOPE;
    return "1. <a href=\""
        + Html.escape(url)
        + "\">Token yaratish sahifasi</a> (nom va <code>api</code> ruxsati tayyor)\n"
        + "2. Muddatini tanlang → <b>Create</b>\n"
        + "3. Tokenni nusxalab shu yerga yuboring\n\n"
        + "🔒 Token shifrlab saqlanadi, xabaringiz chatdan darhol o'chiriladi.";
  }
}
