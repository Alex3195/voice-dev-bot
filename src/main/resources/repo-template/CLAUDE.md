# {{project}}

Bu repo'da kodni **Claude Code agenti** yozadi: voice-dev-bot ovozli buyruqdan GitLab Issue va
spetsifikatsiya yaratadi, agent esa `ai-task` label'li issue bo'yicha feature branch'da ishlab Merge Request ochadi.

## Stack
<!-- Til, framework, build vositasi. Masalan: Java 21, Spring Boot, Gradle. -->

## Qoidalar (kod yozishdan oldin o'qing)
- [.ai/criteria.yml](.ai/criteria.yml) — mashina o'qiydigan talablar (coverage, testlar, CI).
- [.ai/task-template.md](.ai/task-template.md) — issue shabloni va Definition of Done.
- [docs/roadmap.md](docs/roadmap.md) — reja va bosqichlar.
- [docs/decisions/](docs/decisions/) — qabul qilingan qarorlar (nima uchun shunday).
- [docs/specs/](docs/specs/) — har task'ning spetsifikatsiyasi (TZ).

## Ish tartibi
- Bitta task — bitta feature branch (`feature/<qisqa-nom>`), keyin Merge Request. `main`ga to'g'ridan-to'g'ri push yo'q.
- Talab noaniq bo'lsa — kod yozishdan oldin issue'da savol beriladi.
- Task chegarasidan chiqilmaydi: yo'l-yo'lakay refactor yo'q, topilgan muammo alohida taklif qilinadi.
- Muhim qaror qabul qilinsa — `docs/decisions/`ga yangi yozuv qo'shiladi.

## Buyruqlar
<!-- Build, test, lint, ishga tushirish. Masalan: ./gradlew check -->
