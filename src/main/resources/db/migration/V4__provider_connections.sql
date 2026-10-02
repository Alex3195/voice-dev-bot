-- GitLab ulanishlari umumiy ulanishlarga aylanadi (GitLab, GitHub, keyin Jira).
-- Mavjud ulanishlar GitLab'niki bo'lib qoladi; shifrlangan tokenlar o'zgarmaydi.

alter table gitlab_connection rename to provider_connection;
alter index gitlab_connection_pkey rename to provider_connection_pkey;

alter table provider_connection add column provider text not null default 'GITLAB';
alter table provider_connection alter column provider drop default;

-- Bitta xizmat + server + token egasi — bitta ulanish
alter table provider_connection drop constraint gitlab_connection_base_url_username_key;
alter table provider_connection
    add constraint provider_connection_provider_base_url_username_key
    unique (provider, base_url, username);

-- Repo raqami xizmatga bog'liq emas (GitLab project id, GitHub repository id)
alter table project_repo rename column gitlab_project_id to repo_id;
