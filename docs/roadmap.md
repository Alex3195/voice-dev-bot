# Roadmap

Kelishilgan qarorlar va keyingi PR'lar rejasi. Har PR — alohida feature branch, [agent-workflow.md](agent-workflow.md)
bo'yicha: katta PR'dan oldin qisqa reja ko'rsatiladi, `./gradlew check` yashil, `main`ga merge — faqat inson.

> **Muhim farq:** bu bot repo'si GitHub'da (`Alex3195/voice-dev-bot`). Bot **boshqaradigan** projectlar esa
> GitLab'da — ularning task'lari GitLab Issues, kod o'zgarishi GitLab Merge Request.

## Tayyor

| PR | Nima |
| --- | --- |
| #1, #2 | whisper.cpp STT (dev: native, server: Docker GPU/CPU), large-v3 + beam 5 + lotin prompt; voice, audio fayl, video, video xabar, audio/video hujjat (20 MB) |
| #3 | Projectlar, lug'at, foydalanuvchi tili (PostgreSQL + Flyway, oddiy JDBC); inline tugmali interfeys (`BotConversation` + `BotScreens`), `/` menyu |
| #5 | Transkripsiya jurnali + audio arxivi, project kartochkasi, ⚙️ Sozlamalar (PR B) |
| #6 | GitLab ulanishlari (shifrlangan token, muddat ogohlantirishi), project ↔ repo (PR C) |
| #7 | ✅ Tasklar (Issue'lar holat bo'yicha guruhlab, yaratish/yopish, transkriptdan task) va 📄 Hujjatlar (PR E) |
| #9 | Ulanishlar bir nechta provayder uchun: `🔗 Ulanishlar`, `CodeHost` / `IssueTracker`, V4 migratsiya (PR F) |
| #10 | GitHub (github.com): token, repo'lar, hujjatlar, Issues, PR'lar (PR H) |

Tartib: **C → E → D** — GitLab ulanishi hammasining asosi; E tasklar va hujjatlarni qo'lda boshqarishni beradi,
D esa ovozdan task yaratishni qo'shadi. C va E tayyor.

Keyingi tartib: F → G → H → D edi; GitHub kerak bo'lgani uchun H G'dan oldin qilindi. F va H tayyor —
**keyingisi G** (repo'ni URL bilan ulash, GitLab va GitHub uchun birdan), keyin D (ovozdan task).

## Asosiy qarorlar (nima uchun shunday)

- **Whisper o'zbekchada zaif.** Turbo emas, to'liq `large-v3`; prompt'da lotin namunasi (usiz kirill/turkcha
  imloga o'tadi) + faol project lug'ati (xususiy nomlar: "ELT imzo", "Klaes", "PVX"). Sinovda lug'at atamalar
  aniqligini sezilarli oshirdi.
- **Qoraqalpoqcha nutq** — Whisper'da bu til yo'q, `auto` uni qozoqcha deb aniqlaydi. Bunday user uchun `/lang kk`;
  matnni o'zbekcha task'ga Claude aylantiradi. `language=auto` hamma uchun yoqilmaydi (qisqa o'zbekcha gaplarni
  turkcha/qozoqcha deb adashtiradi).
- **Whisper matni ~80% tushunarli bo'lsa yetarli** — ma'noni Claude tiklaydi, foydalanuvchi ✅/✏️ bilan tasdiqlaydi.
  Raqamlar va nomlar tasdiqlashda albatta ko'rsatiladi (Whisper ularda eng ko'p adashadi).
- **Lug'at avtomatik "so'rash" bilan emas, "taklif" bilan to'ldiriladi** — har audio'dan keyin so'rash charchatadi.
  Claude tuzatgan atamalardan audio ostida ≤ 3 ta bir bosishli taklif.
- **Audio va matn muddatsiz saqlanadi** (disk, Docker volume) — lug'at takliflari, sozlamalarni o'lchash va
  kelajakda Whisper fine-tuning dataset (shevalar, qoraqalpoqcha) uchun.

## PR B — transkripsiya jurnali va project kartochkasi

- [ ] Port `AudioArchive` + disk adapteri: `data/audio/<yyyy-mm-dd>/<id>.<ext>` (Docker volume; keyin MinIO/S3
      adapteri bilan almashtirish mumkin).
- [ ] `transcription` jadvali: user, project, til, yuborilgan prompt, model/sozlama, xom matn, audio yo'li,
      Telegram `file_id` (audio'ni qayta ko'rsatish uchun), davomiylik, vaqt. Bo'sh ustunlar keyingi PR uchun:
      `corrected_text`, `confirmed_text`, `llm_usage` (JSON).
- [ ] Jurnalga yozib bo'lmasa bot ishi to'xtamaydi — log + foydalanuvchi baribir matnni oladi.
- [ ] Project kartochkasi (project tugmasi bosilganda): `📝 Transkriptlar · 📖 Lug'at · ✅ Tasklar · 📄 Hujjatlar`
      (oxirgi ikkitasi hozircha "tez orada"). Transkriptlar ro'yxati sahifalab, bosilsa matn + audio (`file_id`).
- [ ] Bosh menyuga `⚙️ Sozlamalar` (til shu yerga ko'chadi; keyin GitLab va model).

## PR C — GitLab ulanishlari

- [ ] Istalgancha ulanish: gitlab.com, self-hosted yoki boshqa istalgan GitLab — `{base_url, token}`.
- [ ] Token qo'shilganda/yangilanganda darhol tekshiriladi (`GET /user`, `GET /personal_access_tokens/self` →
      egasi, scope, `expires_at`). Kerakli scope: `api`.
- [ ] Tokenlar bazada **shifrlangan** (AES-GCM, kalit `.env`da: `SECRETS_KEY`); logga, javobga tushmaydi, botda
      faqat `glpat-…a1b2`; token yozilgan xabar chatdan bot tomonidan o'chiriladi.
- [ ] Tugashiga 7 kun qolganda ogohlantirish; tugagan bo'lsa amal o'rniga `🔑 Tokenni yangilash` tugmasi.
- [ ] `⚙️ Sozlamalar → 🔗 GitLab`: ro'yxat (holati, tugash sanasi), ➕ qo'shish, 🔑 yangilash, 🗑 o'chirish.
- [ ] Project ↔ repo: project qo'shishda `📂 Mavjud repo'ni tanlash` (qidiruv bilan) / `➕ Yangi repo yaratish`
      (`CLAUDE.md`, `.ai/criteria.yml`, `.ai/task-template.md` shablonlari bilan;
      `Alex3195/ai-agent-workflow` asos bo'la oladi) / `⏭ Keyinroq`.

## PR E — Tasklar va Hujjatlar bo'limlari (tayyor, #7)

- [x] **✅ Tasklar** (project kartochkasida): project repo'sidagi barcha GitLab Issue'lar **holat bo'yicha
      guruhlab**, har guruh soni bilan, ichida sahifalab. Holat GitLab'dagi haqiqiy holatdan:
      ⏰ muddati o'tgan · 🟢 ochiq · 🔀 MR ochilgan · ✅ bajarilgan (MR bilan yopilgan) · ⚪ yopiq.
      Ro'yxat bitta so'rov bilan (`merge_requests_count`, oxirgi 300 ta); MR'ning aniq holati task ochilganda.
- [x] `➕ Yangi task` — sarlavha + tavsif (matn bilan), tasdiqdan keyin Issue (`ai-task` label). Transkript
      ostida `✅ Task yaratish` (PR D'gacha — birinchi gap sarlavha, to'liq matn tavsif; PR D'dan keyin Claude
      bilan). Tasdiq ekranida `✏️ Sarlavha` / `✏️ Tavsif`.
- [x] Task ichida: tavsif, MR havolasi, `✔️ Yopish` / `↩️ Qayta ochish`.
- [ ] Task'ga muddat (`due_date`) qo'yish botdan — hozircha faqat GitLab'da.
- [x] **📄 Hujjatlar** — project repo'sidan o'qiladi (botda nusxa yo'q, repo yagona manba). Standart tuzilma:
      `docs/roadmap.md`, `docs/decisions/NNN-*.md` (qarorlar, ADR), `docs/specs/NNN-*.md` (TZ/spetsifikatsiya),
      `CLAUDE.md`. Uzun hujjat bo'laklab ko'rsatiladi.

## PR F — Ulanishlar: bir nechta provayder (tayyor, #9)

- [x] Har ulanishda provayder turi bor: GitLab, GitHub, keyin Jira. Bitta ulanish (server + token egasi) shu
      serverdagi **barcha projectlar** uchun ishlatiladi — token har project uchun qayta so'ralmaydi.
- [x] `⚙️ Sozlamalar → 🔗 Ulanishlar`: hamma provayderlar bitta ro'yxatda — holati (🟢 faol · ⚠️ tugayapti ·
      ⛔ tugagan) va tugash sanasi bilan; ➕ qo'shish, 🔑 yangilash, 🗑 o'chirish.
- [x] Port ikkiga bo'linadi: `CodeHost` (repo, fayllar, MR/PR) va `IssueTracker` (tasklar). Jira faqat
      `IssueTracker` bo'ladi — project kodi bir joyda, tasklari boshqa joyda bo'lishi mumkin.
- [x] Mavjud GitLab ulanishlari migratsiya bilan saqlanadi (`provider_connection`, V4).
- Qaror: `SECRETS_KEY` (tokenlarni shifrlash kaliti) `.env`da qoladi — bir marta, `openssl rand -base64 32`;
  kalit bazada bo'lsa shifrlash ma'nosiz. Git tokenlarining o'zi — faqat Settings orqali, bazada.

## PR G — Repo'ni URL bilan ulash (keyingi)

- [ ] Repo URL yuboriladi → bot provayder (GitLab/GitHub), server va repo yo'lini o'zi aniqlaydi
      (`https://github.com/egasi/nomi`, `https://git.firma.uz/guruh/ichki/nomi`, `.git` va `/-/…` qismlari bilan ham).
      Self-hosted server qaysi provayder ekanini bilish uchun avval mavjud ulanishlar tekshiriladi.
- [ ] O'sha server uchun faol token bo'lsa — darhol ulanadi; yo'q yoki tugagan bo'lsa — token so'raladi,
      tekshiriladi, saqlanadi (muddati tugaguncha ishlatiladi), keyin repo ulanadi.
- [ ] Ro'yxatdan tanlash va yangi repo yaratish ham qoladi.

## PR H — GitHub (github.com) (tayyor, #10)

- [x] GitHub token (fine-grained yoki classic `repo`), tekshirish va muddat — GitLab kabi.
- [x] Repo'lar, hujjatlar, Issues (tasklar) va Pull Request'lar. GitHub'da issue muddati yo'q — "⏰ muddati
      o'tgan" guruhi bo'lmaydi; "MR ochilgan" — `linked:pr` qidiruvi, PR'lar — issue timeline'idan.
- [ ] GitHub Enterprise — hozircha yo'q, keyin qo'shilishi mumkin.

## PR D — Claude TaskParser

- [ ] SDK: `com.anthropic:anthropic-java` (yangi dependency — ruxsat bilan). Kalit: `ANTHROPIC_API_KEY` `.env`da.
- [ ] **Model tanlash** — Claude CLI'dagi `/model` kabi: `/model` va `⚙️ Sozlamalar → 🤖 Model`, ro'yxat Models
      API'dan (`models.list`), har foydalanuvchi uchun alohida. **Standart: `claude-opus-5-5`**.
- [ ] Natija **structured outputs** bilan (`output_config.format`, JSON sxema): `project, title, description,
      acceptance_criteria, type, corrections[] (xato → to'g'ri), task_summary`.
- [ ] **Effort** `low`/`medium` dan boshlanadi (Opus 5.5 default'i `medium`); haqiqiy namunalarda o'lchab sozlanadi.
      `stop_reason: "refusal"`ni tekshirish, server-side fallback yoqiladi.
- [ ] **Prompt caching** — barqaror qism oldinda, har bayt o'zgarishi undan keyingi keshni buzadi:
      1. tizim ko'rsatmasi + sxema (deyarli o'zgarmaydi) — breakpoint 1;
      2. project qoidalari (`CLAUDE.md`, `criteria.yml` — repo'dan);
      3. project xotirasi + lug'at (har task'da o'sadi) — breakpoint 2;
      4. yangi transkript (keshlanmaydi).
      Opus 5.5'da minimum 512 token; o'qish $0.20/MTok, yozish 1.25× (5 daqiqa) / 2× (1 soat). TTL — jurnaldagi
      haqiqiy so'rov oralig'iga qarab tanlanadi. `usage.cache_read_input_tokens` bilan tekshiriladi.
- [ ] **Project xotirasi** (butun tarix yuborilmaydi, lekin hech narsa yo'qolmaydi):
      1. har task'ning qisqa xulosasi — o'sha javobning o'zida (`task_summary`), bazada;
      2. project xulosasi (qilingan ishlar, qarorlar, ochiq masalalar) — har N task'dan keyin qayta yoziladi
         (Batch API, 50% arzon), prompt'da doim turadi va keshlanadi;
      3. yangi transkriptga eng o'xshash 3–5 eski task xulosasi — Postgres full-text qidiruvi.
- [ ] **Spetsifikatsiya** (`.ai/task-template.md` formatida) → project repo'siga `docs/specs/<raqam>-<nom>.md`
      (MR orqali) + **GitLab Issue** (`ai-task` label, spetsifikatsiyaga havola). Project kartochkasidagi
      `📄 Hujjatlar` — shu spetsifikatsiyalar.
- [ ] Transkript ostida: `✅ Task yaratish` / `✏️ Tahrirlash` (tasdiqsiz hech narsa yaratilmaydi) va lug'atga
      `💡` takliflar (`corrections`dan, ≤ 3 ta).
- [ ] Har chaqiruvning `usage`i (input, cache read/write, output) jurnalga — har task narxi ko'rinadi.

## Keyin

- **Jira** — faqat tasklar uchun (`IssueTracker`); Cloud yoki Server, project ↔ Jira loyiha bog'lanishi — boshlashdan
  oldin aniqlanadi.
- GitHub Enterprise (self-hosted GitHub).

- GitLab CI + Claude Code headless: `ai-task` label → agent feature branch'da kod yozadi → MR → CI → staging.
- Prod'ga faqat qo'lda tasdiq bilan (GitLab protected environments).
- Uzun audio uchun "⏳ qayta ishlanmoqda" xabari (whitelist tekshiruvidan keyin) va parallel qayta ishlash.
- 20 MB'dan katta fayllar — lokal Telegram Bot API server.
- Whisper fine-tuning — yetarli tasdiqlangan audio yig'ilgach (bir necha soat).
