# Arxitektura: Hexagonal (Ports & Adapters)

Maqsad — biznes mantiq (whitelist, ovoz → task oqimi) tashqi tizimlardan mustaqil bo'lsin.
STT dvigateli, LLM, tracker (GitHub/Jira) almashtirilganda yadro kodi o'zgarmasligi kerak.

## Qatlamlar

```
com.alex.voicedevbot
├── domain/                 Sof Java: value object, entity, biznes qoidalar
├── application/
│   ├── port/in/            Use-case interfeyslari + ularning kirish/chiqish modellari
│   ├── port/out/           Tashqi dunyoga kerak bo'lgan narsalar (interfeys)
│   └── service/            Use-case implementatsiyalari
├── adapter/
│   ├── in/<texnologiya>/   Tashqaridan chaqiradi: Telegram bot, (keyin) webhook, CLI
│   └── out/<texnologiya>/  port/out implementatsiyalari: Telegram fayl, Whisper, Claude, GitHub
└── config/                 Spring wiring: @Configuration, @ConfigurationProperties
```

| Qatlam | Nimaga bog'liq bo'lishi mumkin | Nimaga **mumkin emas** |
| --- | --- | --- |
| `domain` | faqat `java.*` | Spring, Telegram, HTTP, `application`, `adapter` |
| `application` | `domain`, `java.*` | Spring, framework'lar, `adapter`, `config` |
| `adapter` | `application.port`, `domain`, tashqi kutubxonalar | boshqa adapter, `config` |
| `config` | hammasi | — |

Bog'liqlik yo'nalishi doim ichkariga: `config → adapter → application → domain`.

Bu qoidalarni `HexagonalArchitectureTest` (ArchUnit) majburlaydi — buzilsa `./gradlew check` yiqiladi.

## Joriy oqim

```
Telegram Update
  → adapter/in/telegram/VoiceDevBot          (Update → VoiceMessage)
  → application/port/in/HandleVoiceMessageUseCase
      HandleVoiceMessageService:
        1. AccessPolicy.isAllowed(sender)    (whitelist — yuklab olishdan OLDIN)
        2. AudioSource.fetch(ref)            → adapter/out/telegram/TelegramAudioSource
        3. SpeechToText.transcribe(audio)    → adapter/out/stt/WhisperCppSpeechToText  (STT_ENGINE=stub → StubSpeechToText)
  ← VoiceHandlingResult (sealed: Transcribed | AccessDenied)
  → VoiceDevBot javob yuboradi
```

## Qoidalar

1. **Spring faqat `config`da.** Service va adapterlar oddiy Java class; `@Component`/`@Service`
   qo'yilmaydi. Bean'lar `config/*Config.java` da `@Bean` metod bilan yig'iladi. Shunda yadro
   Spring'siz unit-test qilinadi va wiring bir joyda ko'rinadi.
2. **Portni ehtiyoj egasi belgilaydi.** `port/out` interfeysi use-case tilida yoziladi
   (`AudioSource.fetch(AudioRef)`), texnologiya tilida emas (`getFile(fileId)` emas).
3. **Port faqat haqiqiy ehtiyoj paydo bo'lganda qo'shiladi** — kelajak uchun bo'sh port yaratilmaydi.
4. **Adapter tashqi exception'ni port exception'iga tarjima qiladi.** Masalan `TelegramApiException`
   → `AudioUnavailableException`. Yadro tashqi kutubxona exception'larini ko'rmaydi.
5. **Adapterlar bir-birini chaqirmaydi.** Kerak bo'lsa ular use-case orqali bog'lanadi.
6. **Tashqi modellar chegaradan o'tmaydi.** Telegram `Update`/`Voice` faqat `adapter/in/telegram`da;
   ichkariga `VoiceMessage`/`AudioRef` bo'lib kiradi.

## Yangi integratsiya qo'shish (masalan, Whisper yoki Jira)

1. Kerakli port bormi? Yo'q bo'lsa, `application/port/out`ga use-case tilidagi interfeys qo'shing.
2. `adapter/out/<texnologiya>/` da implementatsiya yozing; tashqi xatolarni port exception'iga o'giring.
3. `config`da bean'ni almashtiring yoki property bo'yicha tanlang (`@ConditionalOnProperty`).
4. Adapter uchun WireMock integration testi, service uchun unit test (portni mock qilib).
5. Yadro (`domain`, `application/service`) o'zgarmasligi kerak — o'zgarsa, port noto'g'ri loyihalangan.
