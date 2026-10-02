-- GitLab ulanishlari va project ↔ repo bog'lanishi.

create table gitlab_connection (
    id              bigint generated always as identity primary key,
    base_url        text        not null,
    username        text        not null,
    -- AES-GCM: 12 bayt IV + shifrlangan token (kalit .env'dagi SECRETS_KEY)
    token_encrypted bytea       not null,
    scopes          text[]      not null,
    expires_at      date,
    -- Kunlik ogohlantirish bot qayta ishga tushsa ham bir marta yuborilishi uchun
    last_alerted_on date,
    verified_at     timestamptz not null default now(),
    unique (base_url, username)
);

create table project_repo (
    project_id        bigint primary key references project (id) on delete cascade,
    connection_id     bigint not null references gitlab_connection (id) on delete cascade,
    gitlab_project_id bigint not null,
    path              text   not null,
    web_url           text   not null
);
