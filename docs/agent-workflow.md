# Agent ish tartibi

Bu hujjat kod yozuvchi agent (Claude Code) **qanday ishlashini** belgilaydi. Kod qanday bo'lishi kerakligi —
[architecture.md](architecture.md), [coding-principles.md](coding-principles.md), [testing.md](testing.md)da.
Bu yerdagi qoidalar ulardan ustun emas, ularni qo'llash tartibi.

## 1. Ishdan oldin

- `CLAUDE.md` va yuqoridagi uchta hujjat o'qiladi.
- Bir vaqtda **bitta task**. Uning aniq acceptance criteria'si bo'lishi kerak ([task-template.md](../.ai/task-template.md)).
- Talab noaniq bo'lsa yoki bir nechta to'g'ri yo'l bo'lsa — kod yozishdan **oldin** so'raladi, taxmin qilinmaydi.
- Katta task (bir nechta fayl/port) — avval qisqa reja ko'rsatiladi, tasdiqdan keyin kod.

## 2. Scope — task chegarasidan chiqilmaydi

- Faqat task uchun kerakli fayllar o'zgaradi.
- Yo'l-yo'lakay refactor, nom o'zgartirish, "yaxshilash", boshqa joydagi bug'ni tuzatish — **yo'q**.
  Topilgan muammo javobda alohida taklif sifatida aytiladi.
- Formatlash faqat `./gradlew spotlessApply` orqali va faqat tegilgan fayllarda.

## 3. To'xtab, ruxsat so'rash shart

- Yangi dependency yoki versiya o'zgarishi (`build.gradle.kts`, `gradle/`).
- Yangi port, port interfeysini yoki `domain` modelini o'zgartirish.
- Qoida hujjatlarini o'zgartirish: `CLAUDE.md`, `docs/`, `.ai/`, `.claude/`.
- Tekshiruvlarni yumshatish: JaCoCo chegarasi, ArchUnit qoidasi, Spotless sozlamasi.
- Testni o'chirish, `@Disabled` qo'yish yoki mavjud testni qayta yozish.
- `.env`, sirlar, `application.yml`dagi sozlamalar nomi/ma'nosi.
- Tashqi xizmatga real so'rov (Telegram, Claude API, GitHub) — testlar faqat WireMock/mock bilan.

## 4. Taqiqlangan

- Testni o'tkazish uchun assertion'ni zaiflashtirish yoki kodni testga moslab "aldash".
- Coverage/ArchUnit istisnosi qo'shish.
- `main`ga commit yoki push; `--force` push; `--no-verify`.
- `.env`ni o'qish yoki undagi qiymatlarni chiqarish.

Bularning bir qismi `.claude/settings.json` (deny) va `.claude/hooks/block-main.sh` bilan texnik bloklangan.

## 5. Ish tartibi

1. Test yoziladi (bug bo'lsa — avval yiqiladigan regression test).
2. Kod yoziladi — eng kichik yetarli o'zgarish.
3. `./gradlew spotlessApply`.
4. `./gradlew check` — **yashil bo'lmaguncha "tayyor" deyilmaydi**.
5. Kerak bo'lsa `README`/`.env.example` yangilanadi (yangi sozlama qo'shilganda).

## 6. Git

- Har task uchun branch: `feature/<qisqa-nom>` (bug uchun `fix/<qisqa-nom>`).
- Kichik, mantiqiy commit'lar; xabar — nima va nima uchun.
- Push faqat o'sha feature branch'ga, keyin PR ochiladi. `main`ga birlashtirish — faqat inson.

## 7. Hisobot

Har task oxirida qisqa:
- Nima o'zgardi (fayllar).
- `./gradlew check` natijasi.
- Nima **tekshirilmagan** (masalan, Telegram'da jonli sinov) — ochiq aytiladi.
- Topilgan, lekin scope'dan tashqari muammolar.
