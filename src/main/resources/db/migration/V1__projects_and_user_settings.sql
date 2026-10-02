-- Projectlar, ularning atamalar lug'ati va foydalanuvchi sozlamalari.

create table project (
    id         bigint generated always as identity primary key,
    name       text        not null check (length(trim(name)) between 1 and 100),
    created_at timestamptz not null default now()
);
-- "ELT imzo" va "elt IMZO" — bitta project
create unique index project_name_key on project (lower(name));

create table glossary_term (
    project_id bigint not null references project (id) on delete cascade,
    position   int    not null,
    term       text   not null check (length(trim(term)) between 1 and 100),
    primary key (project_id, position)
);
create unique index glossary_term_key on glossary_term (project_id, lower(term));

create table user_settings (
    telegram_user_id  bigint primary key,
    language          text   not null check (language ~ '^[a-z]{2}$'),
    active_project_id bigint references project (id) on delete set null
);
