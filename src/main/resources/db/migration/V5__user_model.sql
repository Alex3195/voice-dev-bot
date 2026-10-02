-- Har foydalanuvchining Claude modeli (/model); null — standarti (CLAUDE_DEFAULT_MODEL).
alter table user_settings
    add column model text check (model ~ '^[a-z0-9][a-z0-9.-]{0,63}$');
