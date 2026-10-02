package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.port.in.DocsResult;
import java.util.ArrayList;
import java.util.List;

/** Hujjatlar ekranlari: ro'yxat va sahifalab o'qish. Holatsiz, faqat ko'rinish. */
final class DocScreens {

  /**
   * Bitta sahifadagi (escape qilingan) matn. Tahrirlangan xabar 4096 belgidan oshsa Telegram uni
   * rad etadi — sarlavha va tugmalar uchun joy qoldiriladi.
   */
  static final int PAGE_LENGTH = 3500;

  /** Telegram klaviaturasi chegarasidan uzoqroq. */
  static final int MAX_DOCUMENT_BUTTONS = 50;

  private DocScreens() {}

  static Screen list(DocsResult.Listed listed) {
    String body =
        listed.paths().isEmpty()
            ? "Hujjat yo'q. Repo'da <code>CLAUDE.md</code> yoki <code>docs/</code> ichida"
                + " <code>.md</code> fayllar bo'lishi kerak."
            : "Bosilsa — matni.";
    if (listed.paths().size() > MAX_DOCUMENT_BUTTONS) {
      body += "\n<i>Birinchi " + MAX_DOCUMENT_BUTTONS + " tasi ko'rsatildi.</i>";
    }
    List<List<Button>> rows = new ArrayList<>();
    listed.paths().stream()
        .limit(MAX_DOCUMENT_BUTTONS)
        .forEach(path -> rows.add(List.of(new Button("📄 " + path, Actions.document(path, 0)))));
    rows.add(List.of(GitLabScreens.backToCard(listed.project())));
    String html =
        "📄 "
            + Html.bold(listed.project().value())
            + " — hujjatlar\n📂 <a href=\""
            + Html.escape(listed.repo().webUrl().toString())
            + "\">"
            + Html.escape(listed.repo().path())
            + "</a>\n\n"
            + body;
    return new Screen(html, rows);
  }

  /**
   * @param page 0 dan; oxirgisidan katta bo'lsa — oxirgi sahifa
   */
  static Screen page(DocsResult.Opened opened, int page) {
    List<String> pages = pages(Html.escape(opened.content()));
    int current = Math.clamp(page, 0, pages.size() - 1);
    String counter = pages.size() > 1 ? " · " + (current + 1) + "/" + pages.size() : "";
    List<List<Button>> rows = new ArrayList<>();
    List<Button> navigation = new ArrayList<>();
    if (current > 0) {
      navigation.add(new Button("◀️", Actions.document(opened.path(), current - 1)));
    }
    if (current < pages.size() - 1) {
      navigation.add(new Button("▶️", Actions.document(opened.path(), current + 1)));
    }
    if (!navigation.isEmpty()) {
      rows.add(navigation);
    }
    rows.add(List.of(new Button("⬅️ Hujjatlar", Actions.DOCS)));
    return new Screen(
        "📄 " + Html.bold(opened.path()) + counter + "\n\n" + pages.get(current), rows);
  }

  /**
   * Escape qilingan matnni bo'laklaydi: iloji bo'lsa qator chegarasidan, keyin so'zdan; HTML
   * belgisi ({@code &amp;}) o'rtasidan bo'linmaydi.
   */
  static List<String> pages(String escaped) {
    List<String> pages = new ArrayList<>();
    String rest = escaped.strip();
    while (rest.length() > PAGE_LENGTH) {
      int cut = rest.lastIndexOf('\n', PAGE_LENGTH);
      if (cut <= 0) {
        cut = rest.lastIndexOf(' ', PAGE_LENGTH);
      }
      if (cut <= 0) {
        cut = PAGE_LENGTH;
        int entity = rest.lastIndexOf('&', cut - 1);
        if (entity > 0 && rest.indexOf(';', entity) >= cut) {
          cut = entity;
        }
      }
      pages.add(rest.substring(0, cut).strip());
      rest = rest.substring(cut).strip();
    }
    pages.add(rest.isEmpty() && pages.isEmpty() ? "<i>bo'sh</i>" : rest);
    return pages.stream().filter(chunk -> !chunk.isEmpty()).toList();
  }
}
