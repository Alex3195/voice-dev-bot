-- Transkripsiya jurnali: har bir audio va undan olingan matn muddatsiz saqlanadi
-- (lug'at takliflari, sozlamalarni o'lchash, keyin Whisper fine-tuning uchun).

create table transcription (
    id               bigint generated always as identity primary key,
    telegram_user_id bigint      not null,
    -- Project o'chirilsa transkript "projectsiz"ga o'tadi, yo'qolmaydi
    project_id       bigint      references project (id) on delete set null,
    language         text        not null check (language ~ '^[a-z]{2}$'),
    prompt           text        not null,
    model            text        not null,
    raw_text         text        not null,
    -- Arxivga yozib bo'lmagan bo'lsa null; audio baribir telegram_file_id orqali ko'rinadi
    audio_path       text,
    telegram_file_id text        not null,
    audio_kind       text        not null,
    mime_type        text        not null,
    duration_seconds int         not null check (duration_seconds >= 0),
    created_at       timestamptz not null,
    -- Keyingi PR (Claude TaskParser) uchun
    corrected_text   text,
    confirmed_text   text,
    llm_usage        jsonb
);
create index transcription_project_idx on transcription (project_id, created_at desc, id desc);
