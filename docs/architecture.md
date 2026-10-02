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
        3. TranscriptionHintsResolver        (foydalanuvchi tili + faol project lug'ati)
             UserSettingsRepository, ProjectRepository → adapter/out/persistence (PostgreSQL)
        4. SpeechToText.transcribe(audio, hints) → adapter/out/stt/WhisperCppSpeechToText  (STT_ENGINE=stub → StubSpeechToText)
             ← Transcription (matn + yuborilgan prompt + model)
        5. TranscriptJournal (xatosi oqimni to'xtatmaydi, faqat log)
             AudioArchive.store   → adapter/out/archive/DiskAudioArchive
             TranscriptionLog.append → adapter/out/persistence/JdbcTranscriptionLog
  ← VoiceHandlingResult (sealed: Transcribed(matn, jurnal raqami) | AccessDenied)
  → VoiceDevBot javob yuboradi

Telegram matn / "/buyruq" / inline tugma
  → adapter/in/telegram/BotConversation       (ekranlar: BotScreens)
      Ulanishlar va repo tugmalari ("cn…") va kutilayotgan kiritish → ConnectionsDialog (ConnectionScreens)
      Tasklar ("tk…", yangi task qoralamasi) → TaskDialog (TaskScreens); hujjatlar ("dc…") → DocsDialog (DocScreens)
  → ManageProjectsUseCase | ManageGlossaryUseCase | ChangeLanguageUseCase | BrowseTranscriptsUseCase
    | ManageConnectionsUseCase | LinkRepoUseCase | ManageTasksUseCase | BrowseDocsUseCase
                                                               (har biri whitelist'ni tekshiradi)
      Tasklar va hujjatlar → ProjectRepoAccess (whitelist, faol project, repo, token; xato → RepoUnavailable)
      Integrations: ulanishning provayderi bo'yicha adapter (config'da ro'yxatga olinadi)
        CodeHost (token, repo, fayllar, MR/PR) + IssueTracker (tasklar):
          GITLAB → adapter/out/gitlab/GitLabHttpApi (REST v4, PRIVATE-TOKEN)
          GITHUB → adapter/out/github/GitHubHttpApi (REST, Bearer; api.github.com)
      ConnectionRepository → JdbcConnectionRepository (provider_connection; token: TokenCipher, AES-GCM)
      ProjectRepoLinks → JdbcProjectRepoLinks; RepoTemplate → adapter/out/template/ClasspathRepoTemplate
  ← sealed natija → Screen (AccessDenied → jim); token yozilgan xabar VoiceDevBot tomonidan o'chiriladi

Rejalashtirilgan (config'dagi ScheduledExecutorService, soatiga bir marta)
  → adapter/in/telegram/TokenExpiryNotifier
  → TokenExpiryAlertsUseCase (har ulanish uchun kuniga bir marta — sana bazada belgilanadi)
  → VoiceDevBot.notify → whitelist'dagi har bir user
```

`application` qatlami SLF4J'ga bog'lana olmaydi (ArchUnit), shuning uchun u yerda yagona log —
`TranscriptJournal`dagi JDK `System.Logger` (Spring Boot uni SLF4J'ga yo'naltiradi).

Persistence adapteri oddiy JDBC (`javax.sql.DataSource`) — Spring faqat `config`da bo'lgani uchun
`JdbcClient` ishlatilmaydi. DataSource va Flyway migratsiyalarini (`src/main/resources/db/migration`)
Spring Boot `config` darajasida sozlaydi.

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
