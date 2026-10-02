# voice-dev-bot

> Telegram voice bot: ovozli buyruqni matnga, matnni task'ga (GitHub Issue / Jira) aylantiradi,
> keyin agent kod yozadi, testlaydi va deploy qiladi.

To'liq g'oya va bosqichlar: [CLAUDE.md](CLAUDE.md).

## Hozir nima ishlaydi

- Whitelist'dagi Telegram user'dan voice qabul qilinadi, boshqalar e'tiborsiz qoldiriladi.
- Audio Telegram'dan yuklanadi va STT portiga beriladi (hozircha stub — Whisper keyingi bosqich).
- Bot natija matnini qaytaradi.

## Talablar

- JDK 21 (Gradle daemon ham 21 da ishlaydi — `gradle/gradle-daemon-jvm.properties`)
- Telegram bot tokeni ([@BotFather](https://t.me/BotFather))
- O'z Telegram user ID'ingiz ([@userinfobot](https://t.me/userinfobot))

## Ishga tushirish

```bash
cp .env.example .env    # token va user ID'ni yozing
./gradlew bootRun
```

## Sozlamalar

Spring `.env` faylni avtomatik o'qiydi (`spring.config.import`); serverda oddiy environment variable ham ishlaydi.

| Variable | Tavsif | Default |
| --- | --- | --- |
| `TELEGRAM_BOT_TOKEN` | BotFather bergan token | — (majburiy) |
| `TELEGRAM_ALLOWED_USER_IDS` | Vergul bilan ajratilgan ruxsat berilgan user ID'lar | — (majburiy) |
| `BOT_API_URL` | Telegram Bot API manzili | `https://api.telegram.org` |
| `BOT_POLLING_ENABLED` | `false` — Telegram'ga ulanmaslik | `true` |

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
