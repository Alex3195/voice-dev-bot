# voice-dev-bot

> Telegram voice bot: ovozli buyruqni matnga, matnni task'ga (GitHub Issue / Jira) aylantiradi,
> keyin agent kod yozadi, testlaydi va deploy qiladi.

To'liq g'oya va bosqichlar: [CLAUDE.md](CLAUDE.md).

## Hozir nima ishlaydi

- Whitelist'dagi Telegram user'dan audio qabul qilinadi, boshqalar e'tiborsiz qoldiriladi:
  voice, audio fayl (mp3, m4a...), video, video xabar, audio/video hujjat. Limit — 20 MB (oddiy Bot API).
- Uzun matn 4096 belgilik bir nechta xabarga bo'linadi. Audio'lar navbat bilan qayta ishlanadi.
- Audio Telegram'dan yuklanadi va whisper.cpp server orqali matnga aylantiriladi.
- Bot natija matnini qaytaradi.
- Projectlar va atamalar lug'ati (PostgreSQL): faol project lug'ati Whisper'ga beriladi — xususiy nomlar
  (ELT imzo, Klaes, PVX) to'g'ri yoziladi.
- Har foydalanuvchining nutq tili (`/lang`): masalan, qoraqalpoqcha gapiradigan uchun `kk`.

### Boshqaruv — tugmalar bilan

`/start` bosh menyuni ochadi: **📁 Projectlar · 📖 Lug'at · 🌐 Til · ❓ Yordam**. Tugma bosilganda o'sha
xabar o'zi yangilanadi (chat to'lib ketmaydi).

- **📁 Projectlar** — project'ni bosib faol qilasiz (✅), **➕ Yangi project** — nomini yozasiz.
- **📖 Lug'at** — **➕ Qo'shish** (atamalarni vergul bilan yozasiz), **➖ O'chirish** (❌ atamani bosasiz).
- **🌐 Til** — 🇺🇿 O'zbek, 🇰🇿 Qozoq / Qoraqalpoq, 🇷🇺 Rus, 🇬🇧 Ingliz, 🇹🇷 Turk.
- Har transkript ostida faol project va til ko'rinadi, **📁 Projectni almashtirish** tugmasi bilan.

Buyruqlar ham ishlaydi (Telegram "Menu"da ko'rinadi): `/start`, `/project [nom]`, `/addproject [nom]`,
`/glossary [add|remove a, b]`, `/lang [kod]`, `/help`.

Whitelist'dan tashqaridagi user'ga bot hech qanday buyruq yoki tugmaga javob bermaydi.

## Talablar

- JDK 21 (Gradle daemon ham 21 da ishlaydi — `gradle/gradle-daemon-jvm.properties`)
- Telegram bot tokeni ([@BotFather](https://t.me/BotFather))
- O'z Telegram user ID'ingiz ([@userinfobot](https://t.me/userinfobot))
- Whisper: dev'da `brew install whisper-cpp ffmpeg`, serverda Docker (+ NVIDIA Container Toolkit)
- Docker — PostgreSQL uchun (va testlar uchun: Testcontainers)

## Ishga tushirish

```bash
cp .env.example .env              # token va user ID'ni yozing
docker compose up -d postgres     # baza (localhost:5433), migratsiyalar bot ishga tushganda
scripts/whisper-dev.sh            # alohida terminalda: native Whisper (dev)
./gradlew bootRun
```

## Whisper (STT)

Bot whisper.cpp `whisper-server`ga HTTP orqali ulanadi. Qayerda ishlashi muhim emas — faqat `STT_WHISPER_URL` o'zgaradi.

**Whisper modeli** (bir marta yuklab olinadi):

```bash
mkdir -p ~/.local/share/whisper-models && cd ~/.local/share/whisper-models
curl -LO https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-q5_0.bin
```

| Muhit | Ishga tushirish | `STT_WHISPER_URL` |
| --- | --- | --- |
| Dev (Mac) | `scripts/whisper-dev.sh` — native, Metal GPU | `http://127.0.0.1:8178` |
| Server (Windows + NVIDIA) | `WHISPER_MODELS_DIR=<model papkasi> WHISPER_BIND=<tailscale IP> docker compose --profile gpu up -d` | `http://<tailscale IP>:8178` |
| GPU'siz mashina | `docker compose --profile cpu up -d` | `http://127.0.0.1:8178` |

Docker sozlamalari: `WHISPER_MODELS_DIR` (default `./models`), `WHISPER_MODEL`, `WHISPER_BIND`, `WHISPER_PORT` (default `8178`).
Rasmiy image faqat amd64 — Apple Silicon'da emulyatsiya juda sekin, dev uchun native ishlating.

**O'zbekcha aniqlik.** Whisper o'zbek tilida zaifroq, shuning uchun:
- to'liq `large-v3` (turbo emas) va `--beam-size 5` ishlatiladi;
- bot har so'rov bilan lotin yozuvidagi namuna gapni prompt sifatida yuboradi (aks holda matn goh kirill, goh turkcha imloda chiqadi). Project atamalari lug'ati — keyingi bosqich (har project uchun bazada).

**STT dvigatellarini o'lchash** (`./gradlew sttBenchmark`). Tasdiqlangan transkriptlarning audiosi arxivdan qayta o'tkaziladi va so'z xatosi foizi (WER) hisoblanadi; Telegram bot ko'tarilmaydi. Hisobot: `build/reports/stt-benchmark/<vaqt>.md` (transkript matnlari bor — repo'ga tushmaydi). Standart holatda faqat `STT_WHISPER_URL` serveri; boshqa model bilan solishtirish — har model o'z portida:

```bash
WHISPER_MODEL=ggml-large-v3.bin WHISPER_PORT=8179 scripts/whisper-dev.sh
./gradlew sttBenchmark --args='--stt.benchmark.engines[0].url=http://127.0.0.1:8178 --stt.benchmark.engines[0].model=ggml-large-v3-q5_0 --stt.benchmark.engines[1].url=http://127.0.0.1:8179 --stt.benchmark.engines[1].model=ggml-large-v3'
```

## Sozlamalar

Spring `.env` faylni avtomatik o'qiydi (`spring.config.import`); serverda oddiy environment variable ham ishlaydi.

| Variable | Tavsif | Default |
| --- | --- | --- |
| `TELEGRAM_BOT_TOKEN` | BotFather bergan token | — (majburiy) |
| `TELEGRAM_ALLOWED_USER_IDS` | Vergul bilan ajratilgan ruxsat berilgan user ID'lar | — (majburiy) |
| `BOT_API_URL` | Telegram Bot API manzili | `https://api.telegram.org` |
| `BOT_POLLING_ENABLED` | `false` — Telegram'ga ulanmaslik | `true` |
| `STT_ENGINE` | `whisper-cpp` yoki `stub` (Whisper'siz) | `whisper-cpp` |
| `STT_WHISPER_URL` | whisper-server manzili | `http://127.0.0.1:8178` |
| `STT_DEFAULT_LANGUAGE` | `/lang` tanlamagan foydalanuvchi tili | `uz` |
| `DB_URL` | PostgreSQL JDBC manzili | `jdbc:postgresql://127.0.0.1:5433/voicedevbot` |
| `DB_USER` / `DB_PASSWORD` | Baza foydalanuvchisi (serverda parolni albatta o'zgartiring) | `voicedevbot` |
| `STT_WHISPER_TIMEOUT` | Bitta audio uchun maksimal vaqt | `15m` |

Majburiy sozlama bo'lmasa ilova ishga tushmaydi. `.env` commit qilinmaydi.

## Loyiha tuzilmasi

Hexagonal (Ports & Adapters) — batafsil [docs/architecture.md](docs/architecture.md).

```
src/main/java/com/alex/voicedevbot/
├── domain/        sof Java value object'lar va qoidalar
├── application/   use-case'lar va portlar
├── adapter/       Telegram (in), Telegram fayl / STT (out)
└── config/        Spring wiring va sozlamalar
```

## Development

```bash
./gradlew check            # format + testlar + ArchUnit + coverage ≥ 80% (Docker kerak: Testcontainers)
./gradlew spotlessApply    # formatlash
```

Qoidalar:
- [docs/coding-principles.md](docs/coding-principles.md) — SOLID, clean code
- [docs/testing.md](docs/testing.md) — test va QA
- [.ai/task-template.md](.ai/task-template.md) — issue shabloni va Definition of Done

## Contributing

1. `main`dan branch oching.
2. O'zgarish + testlar; `./gradlew check` yashil bo'lsin.
3. Aniq tavsif bilan PR oching.
