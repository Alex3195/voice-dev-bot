# voice-dev-bot

Telegram voice bot: ovozli buyruq → matn → task (GitHub Issue / Jira) → kod yozish → test → deploy.
Bir nechta project (har xil tilda) bilan ishlaydi; har project o'z qoidalarini o'z repo'sida saqlaydi.

## Stack
- Java 21, Spring Boot 4.1, Gradle (Kotlin DSL, wrapper; daemon JVM `gradle/gradle-daemon-jvm.properties` da 21 ga qotirilgan).
- Telegram: `org.telegram:telegrambots-longpolling` + `telegrambots-client` 10.3 (Spring starter emas — u Boot 3.5 uchun).
- Arxitektura: **Hexagonal (Ports & Adapters)**, paket `com.alex.voicedevbot`.

## Qoidalar (kod yozishdan oldin o'qing)
- [docs/agent-workflow.md](docs/agent-workflow.md) — **agent shu tartibda ishlaydi**: scope, qachon so'rash, git, hisobot.
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
2. STT: `transcribe(audio) -> text` interface orqasida (faster-whisper, `language="uz"`). Dvigatel almashtirilishi oson bo'lsin (lokal Whisper yoki tashqi API).
3. LLM (Claude API) matnni JSON'ga aylantiradi: `{project, title, description, acceptance_criteria, type}`; Whisper xatolarini ham tuzatadi.
4. Bot natijani ko'rsatadi, inline tugma bilan tasdiqlanadi (✅ / ✏️). Tasdiqsiz hech narsa yaratilmaydi.
5. Tasdiqlansa GitHub Issue (yoki Jira) yaratiladi, `ai-task` label bilan.
6. Label bo'yicha agent (Claude Code headless / GitHub Actions) kodni alohida branch'ga yozadi va PR ochadi.
7. CI testlar, staging'ga avtomatik deploy.
8. **Prod'ga faqat qo'lda tasdiq bilan** (GitHub Environments → required reviewers).

## Multi-project dizayn
- Bot ichida `projects.yml`: `{name, repo, tracker (github|jira), stack}`.
- `/project` buyrug'i yoki ovozda project nomi aytilsa LLM ajratadi; topolmasa so'raydi.
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
- Whisper uy kompyuterida (Windows, RTX 3060 8 GB): `faster-whisper`, `large-v3` yoki `large-v3-turbo`, `device="cuda"`, `compute_type="int8_float16"`, `language="uz"`. Model bir marta yuklanadi, so'rovlar navbat bilan.
- Telegram: long polling (ochiq port/domen kerak emas).
- Masofadan boshqarish: Tailscale + OpenSSH (faqat kalit bilan). Servislar NSSM/Task Scheduler "At startup".
- AWS free tier'da Whisper ishlatilmaydi (RAM yetmaydi); kerak bo'lsa faqat bot u yerda, STT uyda (Tailscale orqali).

## Xavfsizlik
- Faqat whitelist Telegram ID'dan buyruq qabul qilinadi.
- Kalitlar (Telegram, Claude, GitHub, Jira) faqat `.env`da, repo'ga commit qilinmaydi (`.gitignore`).
- Agent `main`'ga to'g'ridan-to'g'ri push qila olmasin (branch protection), faqat feature branch + PR.

## Ochiq savollar
- Tracker: avval GitHub Issues, Jira keyinroq?

## Bosqichlar
1. Voice → matn → GitHub Issue (project tanlash va tasdiqlash bilan)
2. Issue → Claude Code → PR
3. CI + staging deploy
4. Prod'ga qo'lda tasdiq bilan chiqarish

## Holat
- 1-bosqich boshlangan: bot whitelist'dagi user'dan voice qabul qiladi, Telegram'dan yuklab, `SpeechToText` portiga beradi va matnni qaytaradi. STT hozircha `StubSpeechToText` (Whisper adapteri keyingi task).
- Keyingi: Whisper adapteri → `TaskParser` (Claude API) → tasdiqlash tugmalari → `IssueTracker` (GitHub).
- Ma'lum muammo: TelegramBots 10.3 `downloadFileAsStream` API manzilini e'tiborsiz qoldiradi va HTTP statusni tekshirmaydi — shuning uchun `TelegramAudioSource` faylni `java.net.http.HttpClient` bilan o'zi yuklaydi.
- Claude GitHub App'iga bu private repo uchun yozish ruxsati berilmagan (push rad etilgan); kod IntelliJ/VS Code'da lokal yoziladi va o'zingiz push qilasiz yoki ruxsat berasiz.
