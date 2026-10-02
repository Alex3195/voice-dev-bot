# Test va QA qoidalari

`./gradlew check` = Spotless + barcha testlar + ArchUnit + JaCoCo coverage (≥ 80% line).
PR faqat `check` yashil bo'lsa qabul qilinadi.

## Test turlari

| Tur | Nimani tekshiradi | Vosita | Namuna |
| --- | --- | --- | --- |
| **Unit** | `domain`, `application/service`, adapter mantig'i | JUnit 5, AssertJ, Mockito | `HandleVoiceMessageServiceTest`, `VoiceDevBotTest` |
| **Integration** | Adapter ↔ haqiqiy HTTP protokol | WireMock (soxta Telegram/Claude/GitHub API) | `TelegramAudioSourceIntegrationTest` |
| **Architecture** | Qatlam qoidalari, taqiqlangan API'lar | ArchUnit | `HexagonalArchitectureTest` |
| **Smoke** | Spring konteksti yig'iladi, majburiy sozlamalar | `@SpringBootTest` | `VoiceDevBotApplicationTests` |

Ma'lumotlar bazasi yoki boshqa infratuzilma paydo bo'lganda — Testcontainers bilan integration testlar
(H2/embedded o'rniga haqiqiy konteyner).

## Qoidalar

1. **Testlar Spring'siz** — `@SpringBootTest` faqat smoke testlarda. Service va adapterlar
   `new` bilan yaratilib, portlar mock qilinadi. Tez va aniq.
2. **Tashqi tarmoqqa chiqish taqiqlangan.** Har tashqi chaqiruv WireMock yoki mock orqali.
   Test internet o'chiq holda ham o'tishi kerak.
3. **Nomlash**: `should_<natija>_when_<holat>` — masalan
   `should_deny_without_downloading_audio_when_sender_is_not_whitelisted`.
4. **Tuzilma**: Given / When / Then (murakkab testlarda `// given` izohlari bilan).
   Bitta test — bitta xatti-harakat.
5. **AssertJ** (`assertThat`) ishlatiladi; `assertEquals`/`assertTrue` emas.
6. **Mock faqat port/tashqi chegarada.** Domain obyektlari (`record`) mock qilinmaydi — haqiqiysi yaratiladi.
7. **Har bir use-case uchun**: muvaffaqiyatli yo'l + har bir xato/rad yo'li.
8. **Har bir adapter uchun**: muvaffaqiyatli javob, tashqi xato (4xx/5xx), xato xabarida sir yo'qligi.
9. **Xavfsizlik testlari majburiy**: whitelist'dan tashqaridagi user hech narsa ishga tushirmasligi
   (`verifyNoInteractions`).
10. **Bug fix = avval yiqiladigan regression test**, keyin tuzatish.
11. **Testlar mustaqil**: tartib, vaqt, global holatga bog'liq emas. `Thread.sleep` yo'q.

## Coverage

- JaCoCo **line ≥ 80%** — `jacocoTestCoverageVerification` `check`ga ulangan, kam bo'lsa build yiqiladi.
- Istisno: `config/**` va `VoiceDevBotApplication` (wiring — smoke test bilan qoplanadi).
- Hisobot: `build/reports/jacoco/test/html/index.html`.
- Coverage maqsad emas, minimum: getter'larni "qoplash" uchun test yozilmaydi; xatti-harakat tekshiriladi.

## QA (qo'lda / staging)

PR birlashtirilishidan oldin, foydalanuvchiga ko'rinadigan o'zgarishlar uchun:

- [ ] Whitelist'dagi akkauntdan voice → kutilgan javob keldi.
- [ ] Boshqa akkauntdan voice → bot jim (logda `non-whitelisted` WARN).
- [ ] Matn, rasm va boshqa xabarlar → bot e'tibor bermaydi.
- [ ] Tashqi xizmat (STT/Telegram) ishlamasa → foydalanuvchiga xato xabari, ilova yiqilmaydi.
- [ ] Loglarda token yoki sir yo'q.

## Buyruqlar

```bash
./gradlew check                                  # hammasi
./gradlew test --tests '*HandleVoiceMessage*'    # bitta test class
./gradlew test --tests '*HexagonalArchitecture*' # faqat arxitektura
./gradlew spotlessApply                          # formatlash
```
