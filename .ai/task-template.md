## Kontekst
<!-- Nima uchun kerak? Qaysi muammo hal qilinadi? -->

## Talab
<!-- Nima qilinishi kerak — foydalanuvchi nuqtai nazaridan. -->

## Acceptance criteria
- [ ] <!-- Tekshirsa bo'ladigan natija, masalan: "Whitelist'dan tashqaridagi user voice yuborsa, bot javob bermaydi" -->

## Texnik eslatmalar
<!-- Qaysi port/adapter/use-case tegiladi? Yangi port kerakmi? (docs/architecture.md) -->

## Test talablari
- [ ] Unit: use-case'ning muvaffaqiyatli va har bir xato yo'li
- [ ] Integration (WireMock): yangi/o'zgargan adapter uchun, 4xx/5xx holati bilan
- [ ] Xavfsizlik: whitelist'dan tashqaridagi user hech narsa ishga tushira olmaydi (agar tegishli bo'lsa)

## Definition of Done
- [ ] `./gradlew check` yashil (Spotless, testlar, ArchUnit, coverage ≥ 80%)
- [ ] Hexagonal qoidalar buzilmagan: Spring faqat `config`da, yadro adapterlarni bilmaydi
- [ ] SOLID / clean code (docs/coding-principles.md): o'lik kod, magic qiymat, `null` qaytarish yo'q
- [ ] Sirlar log/exception'da yo'q; yangi sozlamalar `.env.example` va README'da
- [ ] Kerak bo'lsa `CLAUDE.md` / `docs/` yangilangan
- [ ] Feature branch + PR (to'g'ridan-to'g'ri `main`ga push yo'q)
