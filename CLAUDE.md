# voice-dev-bot

Telegram voice bot: ovozli buyruq → matn → task (GitLab Issue + spetsifikatsiya) → kod yozish → test → deploy.
Bir nechta project (har xil tilda) bilan ishlaydi; har project o'z qoidalarini o'z repo'sida saqlaydi.

## Stack
- Java 21, Spring Boot 4.1, Gradle (Kotlin DSL, wrapper; daemon JVM `gradle/gradle-daemon-jvm.properties` da 21 ga qotirilgan).
- Telegram: `org.telegram:telegrambots-longpolling` + `telegrambots-client` 10.3 (Spring starter emas — u Boot 3.5 uchun).
- Arxitektura: **Hexagonal (Ports & Adapters)**, paket `com.alex.voicedevbot`.

## Qoidalar (kod yozishdan oldin o'qing)
- [docs/agent-workflow.md](docs/agent-workflow.md) — **agent shu tartibda ishlaydi**: scope, qachon so'rash, git, hisobot.
- [docs/roadmap.md](docs/roadmap.md) — **kelishilgan qarorlar va keyingi PR'lar (B, C, D) rejasi**. Yangi task'ni shu yerdan boshlang.
- [docs/architecture.md](docs/architecture.md) — qatlamlar, bog'liqlik yo'nalishi, yangi integratsiya qo'shish.
- [docs/coding-principles.md](docs/coding-principles.md) — SOLID, clean code, Java/Spring qoidalari.
- [docs/testing.md](docs/testing.md) — test turlari, nomlash, coverage, QA.
- [.ai/criteria.yml](.ai/criteria.yml) — mashina o'qiydigan talablar; [.ai/task-template.md](.ai/task-template.md) — issue shabloni va Definition of Done.

## Buyruqlar
- `./gradlew check` — spotless + barcha testlar + ArchUnit + JaCoCo (≥80%). PR'dan oldin yashil bo'lishi shart.
- `./gradlew spotlessApply` — formatlash (google-java-format).
- `./gradlew bootRun` — botni ishga tushirish (`.env` kerak, `.env.example`ga qarang).
- Bitta test: `./gradlew test --tests '*VoiceDevBotTest'`.

## Oqim
1. Telegram bot voice xabarni qabul qiladi (faqat whitelist'dagi Telegram ID).
2. STT: `transcribe(audio) -> text` interface orqasida (whisper.cpp server, `language="uz"`). Dvigatel almashtirilishi oson bo'lsin (lokal Whisper yoki tashqi API).
3. LLM (Claude API, standart `claude-opus-5-5`, foydalanuvchi `/model` bilan tanlaydi) matnni structured output JSON'ga aylantiradi: `{project, title, description, acceptance_criteria, type, corrections, task_summary}`; Whisper xatolarini ham tuzatadi.
4. Bot natijani ko'rsatadi, inline tugma bilan tasdiqlanadi (✅ / ✏️). Tasdiqsiz hech narsa yaratilmaydi.
5. Tasdiqlansa project repo'sida spetsifikatsiya (`docs/specs/`) va **GitLab Issue** yaratiladi, `ai-task` label bilan.
6. Label bo'yicha agent (Claude Code headless / GitLab CI) kodni alohida branch'ga yozadi va Merge Request ochadi.
7. CI testlar, staging'ga avtomatik deploy.
8. **Prod'ga faqat qo'lda tasdiq bilan** (GitLab protected environments).

## Multi-project dizayn
- Projectlar bot bazasida (PostgreSQL): `/addproject`, `/project`. Har project bitta **GitLab yoki GitHub repo**'ga bog'lanadi (mavjudini tanlash, havola bilan ulash yoki yangisini yaratish).
- Ulanishlar bot ichida (`⚙️ → 🔗 Ulanishlar`) boshqariladi: GitLab (gitlab.com yoki self-hosted), GitHub (github.com), keyin Jira — `{provider, base_url, token}`; bitta token shu serverdagi barcha projectlar uchun, tekshiriladi, tugash sanasi kuzatiladi va yangilanadi.
- Har projectning **atamalar lug'ati** bazada (`/glossary`), `.env`da emas. Faol project lug'ati Whisper prompt'iga qo'shiladi; `TaskParser` esa barcha projectlar lug'atini ko'radi va projectni o'zi aniqlaydi.
- `/project` buyrug'i yoki ovozda project nomi aytilsa LLM ajratadi; topolmasa so'raydi.
- Har foydalanuvchining nutq tili (`/lang`): Whisper'da qoraqalpoq tili yo'q, bunday foydalanuvchilar uchun `kk` eng yaqini.
- Qoidalar bot kodida emas, **har project repo'sida**:
  - `CLAUDE.md`: kod uslubi, arxitektura, qilish/qilmaslik.
  - `.ai/criteria.yml`: coverage, QA, CI/CD talablari.
  - `.ai/task-template.md`: issue shabloni.
- Task yaratishda bot tanlangan project'ning `criteria.yml` va `CLAUDE.md`'sini o'qib, acceptance criteria'ni shunga moslaydi.
- `criteria.yml` namunasi:
  ```yaml
  stack: java-spring
  coverage_min: 80
  required_tests: [unit, integration]
  qa: [api-contract, smoke]
  ci: [build, test, lint, sast]
  deploy: {staging: auto, prod: manual-approval}
  ```
- Yangi project qo'shish: `/addproject` yoki `install.sh` (mavjud `Alex3195/ai-agent-workflow` repo'si asos bo'la oladi).
- CI uchun til bo'yicha reusable workflow shablonlari (java, node, flutter) markaziy repo'da.

## Infratuzilma qarorlari
- STT dvigateli: **whisper.cpp `whisper-server`** (HTTP `/inference`, `--convert` bilan OGG/Opus qabul qiladi), ggml model `large-v3-q5_0` (turbo o'zbekchada zaif), `--beam-size 5`, `language=uz`, har so'rovda lotin yozuvidagi namuna prompt (usiz matn kirill/turkcha imloga o'tib ketadi). Project atamalari lug'ati `.env`da emas — har project uchun bazada (`/glossary`), faol project lug'ati prompt'ga qo'shiladi. Bot unga `WhisperCppSpeechToText` orqali HTTP bilan ulanadi.
- Server: uy kompyuterida (Windows, RTX 3060 8 GB) Docker'da — `docker compose --profile gpu up -d` (`compose.yaml`, rasmiy `whisper.cpp:main-cuda` image). Model bir marta yuklanadi.
- Dev (Mac): native `whisper-server` (Metal GPU, Docker'dan ~70x tez) — `scripts/whisper-dev.sh`. Rasmiy image arm64 uchun yo'q.
- Dev va server farqi faqat bitta sozlama: `STT_WHISPER_URL`.
- Telegram: long polling (ochiq port/domen kerak emas).
- Masofadan boshqarish: Tailscale + OpenSSH (faqat kalit bilan). Servislar NSSM/Task Scheduler "At startup".
- AWS free tier'da Whisper ishlatilmaydi (RAM yetmaydi); kerak bo'lsa faqat bot u yerda, STT uyda (Tailscale orqali).

## Xavfsizlik
- Faqat whitelist Telegram ID'dan buyruq qabul qilinadi.
- Kalitlar (Telegram, Claude, shifrlash kaliti) faqat `.env`da, repo'ga commit qilinmaydi (`.gitignore`).
- Ulanish tokenlari (GitLab, GitHub) bazada **shifrlangan** (AES-GCM, kalit `.env`da); logga va javobga tushmaydi, botda niqoblangan; token yozilgan xabar chatdan o'chiriladi.
- Agent `main`'ga to'g'ridan-to'g'ri push qila olmasin (branch protection), faqat feature branch + PR.

## Bosqichlar
1. Voice → matn → GitLab Issue + spetsifikatsiya (project tanlash va tasdiqlash bilan) — batafsil: [docs/roadmap.md](docs/roadmap.md)
2. Issue → Claude Code → Merge Request
3. CI + staging deploy
4. Prod'ga qo'lda tasdiq bilan chiqarish

## Holat
- 1-bosqich boshlangan: bot whitelist'dagi user'dan voice qabul qiladi, Telegram'dan yuklab, `SpeechToText` portiga beradi va matnni qaytaradi. STT — `WhisperCppSpeechToText` (whisper.cpp server); `STT_ENGINE=stub` bilan Whisper'siz ishlatish mumkin. Voice, audio fayl, video, video xabar qabul qilinadi (20 MB gacha).
- Projectlar, lug'at va foydalanuvchi tili — PostgreSQL (`compose.yaml`, port 5433), Flyway, oddiy JDBC (Spring faqat `config`da). Boshqaruv inline tugmalar bilan (`BotConversation` + `BotScreens`; ulanishlar va repo — `ConnectionsDialog` + `ConnectionScreens`; tasklar — `TaskDialog`; hujjatlar — `DocsDialog`), buyruqlar qisqa yo'l sifatida: `/start`, `/project`, `/addproject`, `/glossary`, `/settings`, `/lang`, `/model`, `/help`.
- Har transkript jurnalga yoziladi (`transcription` jadvali: prompt, model, matn, Telegram `file_id`), audio diskda (`AUDIO_ARCHIVE_DIR`). Jurnal xatosi botni to'xtatmaydi. Project kartochkasi: transkriptlar, lug'at, repo; ⚙️ Sozlamalar: til, 🔗 Ulanishlar, projectsiz transkriptlar.
- Ulanishlar (`⚙️ → 🔗 Ulanishlar`): GitLab (gitlab.com / self-hosted, `api` scope) va GitHub (github.com; fine-grained yoki classic `repo`). Bitta ulanish (xizmat + server + token egasi) shu serverdagi barcha projectlar uchun; token xizmatda tekshiriladi, bazada AES-GCM bilan shifrlangan (`SECRETS_KEY` — `.env`da, bir marta), ro'yxatda holati va tugash sanasi, tugashidan 7 kun oldin kunlik ogohlantirish. Adapterlar `CodeHost` + `IssueTracker` portlari orqali, provayder bo'yicha `Integrations` tanlaydi (Jira keyin — faqat `IssueTracker`). Project ↔ repo: mavjudini tanlash, havola bilan ulash (`RepoUrl`; server mavjud ulanishlardan topiladi, yo'q bo'lsa token so'raladi, notanish server provayderi taxmin qilinmaydi) yoki shablon bilan yangisini yaratish (`src/main/resources/repo-template/`).
- ✅ Tasklar (project kartochkasida): repo'dagi Issue'lar (GitLab/GitHub) holat bo'yicha guruhlab (⏰ muddati o'tgan · 🟢 ochiq · 🔀 MR ochilgan · ✅ bajarilgan · ⚪ yopiq), yopish/qayta ochish, `➕ Yangi task` va transkript ostida `✅ Task yaratish` — tasdiqdan keyin, `ai-task` label bilan.
- Ovozdan task (PR D1): `✅ Task yaratish` bosilganda Claude (`DraftTaskUseCase` → `TaskParser` → `adapter/out/claude`, structured outputs, ikki kesh nuqtasi) transkriptdan qoralama tuzadi: sarlavha, talab, acceptance criteria, turi, Whisper tuzatishlari; barcha projectlar lug'ati va faol repo'dagi `CLAUDE.md` / `.ai/criteria.yml` bilan. Tasdiq ekranida tuzatishlar, tokenlar va lug'atga `💡` takliflar. Model — `/model` (har user uchun, standart `claude-opus-5-5`). `ANTHROPIC_API_KEY` bo'lmasa yoki xato bo'lsa — oddiy qoralama. `usage` — `transcription.llm_usage`. 📄 Hujjatlar: repo'dagi `CLAUDE.md`, `README.md`, `docs/**/*.md`, sahifalab.
- Tayyor: PR #1–#3 (STT, audio turlari, projectlar/lug'at/til, tugmali interfeys), #5 (PR B: jurnal, kartochka, sozlamalar), #6 (PR C: GitLab ulanishlari, repo), #7 (PR E: tasklar va hujjatlar), #9 (PR F: ko'p provayderli ulanishlar), #10 (PR H: GitHub), #12 (PR G: repo'ni havola bilan ulash), #14 (PR D1: Claude qoralamasi), #17 (PR I: Claude tuzatgan transkript).
- Tuzatilgan transkript (PR I): Claude qoralama bilan birga `corrected_transcript` ham qaytaradi — so'zlovchi so'zlari, faqat tanib olish xatolari tuzatilgan (tarjima emas), `transcription.corrected_text`ga yoziladi; task `✅ Yaratish` bilan yaratilsa `confirmed_text`ga ko'chadi (STT o'lchash va fine-tuning uchun "to'g'ri javob"). Tasdiq ekrani va transkript kartochkasida `📝 Tuzatilgan matn`.
- **Keyingi: PR J** — arxivdagi audio va `confirmed_text` bo'yicha STT dvigatellarini o'lchash (WER), so'ng D2 (spetsifikatsiya) va D3 (project xotirasi). Sabab: Whisper qoraqalpoqcha talaffuzda ~50–60%, zaif bo'g'in shu. Jira va GitHub Enterprise — keyin. Reja: [docs/roadmap.md](docs/roadmap.md).
- Ma'lum muammo: TelegramBots 10.3 `downloadFileAsStream` API manzilini e'tiborsiz qoldiradi va HTTP statusni tekshirmaydi — shuning uchun `TelegramAudioSource` faylni `java.net.http.HttpClient` bilan o'zi yuklaydi.
- Bot repo'si GitHub'da (`Alex3195/voice-dev-bot`): agent lokal yozadi, feature branch'ga push qiladi va `gh` bilan PR ochadi; `main`ga merge — faqat inson (`.claude/hooks/block-main.sh`).
- Dev'da ishga tushirish: `docker compose up -d postgres`, `scripts/whisper-dev.sh`, `./gradlew bootRun` (`.env`da `SECRETS_KEY` bo'lishi shart — faqat `openssl rand -base64 32` qiymati, qo'lda yozilgan qiymat Base64 xatosi beradi). Testlar Docker talab qiladi (Testcontainers).
