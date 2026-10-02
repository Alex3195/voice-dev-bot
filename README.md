# voice-dev-bot

> Telegram voice bot: ovozli buyruqni matnga, matnni task'ga (GitHub Issue / Jira) aylantiradi,
> keyin agent kod yozadi, testlaydi va deploy qiladi.

To'liq g'oya va bosqichlar: [CLAUDE.md](CLAUDE.md).

## Hozir nima ishlaydi

- Whitelist'dagi Telegram user'dan voice qabul qilinadi, boshqalar e'tiborsiz qoldiriladi.
- Audio Telegram'dan yuklanadi va whisper.cpp server orqali o'zbekcha matnga aylantiriladi.
- Bot natija matnini qaytaradi.

## Talablar

- JDK 21 (Gradle daemon ham 21 da ishlaydi — `gradle/gradle-daemon-jvm.properties`)
- Telegram bot tokeni ([@BotFather](https://t.me/BotFather))
- O'z Telegram user ID'ingiz ([@userinfobot](https://t.me/userinfobot))
- Whisper: dev'da `brew install whisper-cpp ffmpeg`, serverda Docker (+ NVIDIA Container Toolkit)

## Ishga tushirish

```bash
cp .env.example .env    # token va user ID'ni yozing
scripts/whisper-dev.sh  # alohida terminalda: native Whisper (dev)
./gradlew bootRun
```

## Whisper (STT)

Bot whisper.cpp `whisper-server`ga HTTP orqali ulanadi. Qayerda ishlashi muhim emas — faqat `STT_WHISPER_URL` o'zgaradi.

**Whisper modeli** (bir marta yuklab olinadi):

```bash
mkdir -p ~/.local/share/whisper-models && cd ~/.local/share/whisper-models
curl -LO https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin
```

| Muhit | Ishga tushirish | `STT_WHISPER_URL` |
| --- | --- | --- |
| Dev (Mac) | `scripts/whisper-dev.sh` — native, Metal GPU | `http://127.0.0.1:8178` |
| Server (Windows + NVIDIA) | `WHISPER_MODELS_DIR=<model papkasi> WHISPER_BIND=<tailscale IP> docker compose --profile gpu up -d` | `http://<tailscale IP>:8178` |
| GPU'siz mashina | `docker compose --profile cpu up -d` | `http://127.0.0.1:8178` |

Docker sozlamalari: `WHISPER_MODELS_DIR` (default `./models`), `WHISPER_MODEL`, `WHISPER_BIND`, `WHISPER_PORT` (default `8178`).
Rasmiy image faqat amd64 — Apple Silicon'da emulyatsiya juda sekin, dev uchun native ishlating.

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
| `STT_WHISPER_LANGUAGE` | Nutq tili | `uz` |
| `STT_WHISPER_TIMEOUT` | Bitta voice uchun maksimal vaqt | `120s` |

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
./gradlew check            # format + testlar + ArchUnit + coverage ≥ 80%
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
