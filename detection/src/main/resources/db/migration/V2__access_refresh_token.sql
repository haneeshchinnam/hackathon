alter table users add column if not exists refresh_token varchar(255);

alter table users add column if not exists access_token varchar(255);