# Kod tamoyillari

Formatlash bahs mavzusi emas — uni `./gradlew spotlessApply` (google-java-format) hal qiladi.
Bu hujjat kodning **tuzilishi** haqida: SOLID, clean code va loyihaga xos qoidalar.

## SOLID — bu loyihada qanday ko'rinadi

**S — Single Responsibility.** Har class o'zgarishi uchun bitta sabab.
- `VoiceDevBot` — faqat Telegram `Update`ni use-case'ga tarjima qiladi va javob yuboradi.
- `TelegramAudioSource` — faqat faylni yuklab oladi.
- `HandleVoiceMessageService` — faqat oqimni boshqaradi (ruxsat → yuklash → STT).
- Belgisi: class nomida "And"/"Manager"/"Util" bo'lsa yoki metod 20+ qator bo'lsa — bo'ling.

**O — Open/Closed.** Yangi imkoniyat yangi kod bilan qo'shiladi, mavjudini tahrirlab emas.
- Whisper qo'shish = yangi `SpeechToText` adapter + `config`da bean almashtirish. Service o'zgarmaydi.
- Jira qo'shish = yangi `IssueTracker` adapter. `if (tracker == JIRA)` kabi tarmoqlar yozilmaydi.

**L — Liskov Substitution.** Har port implementatsiyasi shartnomani to'liq bajaradi.
- `SpeechToText.transcribe` muvaffaqiyatsiz bo'lsa `TranscriptionException` tashlaydi —
  `null`, bo'sh matn yoki boshqa exception qaytarmaydi. Stub ham, Whisper ham bir xil.

**I — Interface Segregation.** Portlar kichik va maqsadli.
- `AudioSource` (bitta metod) va `SpeechToText` (bitta metod) alohida — bitta "TelegramService" emas.
- Use-case'lar ham alohida: `HandleVoiceMessageUseCase`, keyin `ConfirmTaskUseCase` va h.k.

**D — Dependency Inversion.** Yadro abstraksiyaga bog'liq, adapter esa yadroga.
- `HandleVoiceMessageService` `AudioSource` interfeysini oladi, `TelegramAudioSource`ni bilmaydi.
- Bog'liqliklar faqat konstruktor orqali beriladi.

## Clean code

- **Nomlar** niyatni aytadi: `isAllowed`, `fetch`, `transcribe`. Qisqartma yo'q (`mgr`, `tmp`, `data`).
  Boolean — `is/has/can` bilan. Class — ot, metod — fe'l.
- **Kichik metodlar**: bitta abstraksiya darajasi, ideal ≤ 15 qator, parametrlar ≤ 3.
  Ko'p parametr kerak bo'lsa — `record` yarating.
- **Magic qiymat yo'q**: `static final` konstanta (`DOWNLOAD_TIMEOUT`, `HTTP_OK`) yoki config property.
- **Erta qaytish** (guard clause) chuqur `if` ichma-ichligi o'rniga.
- **Izoh "nima"ni emas, "nima uchun"ni tushuntiradi.** Masalan `TelegramAudioSource`dagi
  kutubxona bug'i haqidagi izoh. O'z-o'zidan tushunarli kodga izoh yozilmaydi.
- **O'lik kod, kommentga olingan kod, `TODO` siz issue** — commit qilinmaydi.
- **YAGNI**: hozir kerak bo'lmagan abstraksiya, port, parametr qo'shilmaydi.

## Java 21

- **Immutability by default**: value object'lar `record`; maydonlar `final`; to'plamlar `Set.copyOf`/`List.copyOf`.
  Massiv saqlanadigan bo'lsa — himoya nusxasi (`AudioClip`ga qarang).
- **Invariantlar konstruktorda** tekshiriladi (compact constructor): noto'g'ri obyekt yaratib bo'lmaydi.
- **`sealed` + `switch` pattern matching** natija turlari uchun (`VoiceHandlingResult`) —
  yangi holat qo'shilsa, kompilyator barcha `switch`larni ko'rsatadi. `default` yozilmaydi.
- **`null` qaytarilmaydi.** Bo'lmasligi mumkin bo'lsa — `Optional<T>` (faqat qaytish qiymati sifatida,
  maydon/parametr sifatida emas) yoki sealed natija.
- `var` — faqat tur o'ng tomonda aniq ko'rinsa.

## Spring

- Faqat **konstruktor injection**. `@Autowired` maydonga — taqiqlangan (ArchUnit tekshiradi).
- Spring annotatsiyalari faqat `config` paketida (qarang: [architecture.md](architecture.md)).
- Sozlamalar — `@ConfigurationProperties` + `@Validated` record (`BotProperties`), `@Value` emas.
  Majburiy sozlama yo'q bo'lsa, ilova ishga tushmasligi kerak.

## Xatolar

- Yadro exception'lari — `RuntimeException`dan meros, port paketida (`AudioUnavailableException`).
- Adapter tashqi exception'ni ushlab, port exception'iga o'giradi va **sababini (`cause`) saqlaydi**.
- Exception yutib yuborilmaydi: yo qayta tashlanadi, yo log qilinib foydalanuvchiga javob beriladi.
- `InterruptedException` ushlansa — `Thread.currentThread().interrupt()` chaqiriladi.

## Log va xavfsizlik

- SLF4J, parametrli xabarlar: `log.warn("... {}", id)` — string birlashtirish emas.
- `System.out`, `java.util.logging` — taqiqlangan (ArchUnit).
- **Sirlar hech qachon log/exception xabariga tushmaydi**: token, API kalit, token bor URL.
  Sirli maydonli record'larda `toString()` niqoblanadi (`BotProperties`).
- Foydalanuvchi ma'lumoti (audio, matn) faqat kerak bo'lganda va qisqa log qilinadi.
- Tashqi so'rovlarda doim timeout bo'ladi.
