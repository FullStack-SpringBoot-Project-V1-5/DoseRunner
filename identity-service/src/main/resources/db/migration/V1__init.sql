-- identity-service schema. Hibernate only validates it (ddl-auto=validate); Flyway owns it.
create table app_user (
    id            uuid         primary key,
    username      varchar(64)  not null unique,
    password_hash varchar(100) not null,
    enabled       boolean      not null default true,
    created_at    timestamptz  not null default now()
);

create table user_role (
    user_id uuid        not null references app_user (id) on delete cascade,
    role    varchar(20) not null check (role in ('NURSE', 'PHARMACIST', 'OPERATOR', 'AUDITOR', 'ADMIN')),
    primary key (user_id, role)
);
