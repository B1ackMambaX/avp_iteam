CREATE TABLE app_user (
    id            bigserial    PRIMARY KEY,
    username      varchar(64)  NOT NULL,
    password_hash varchar(100) NOT NULL,
    role          varchar(16)  NOT NULL CHECK (role IN ('ADMIN','EDITOR','READER')),
    enabled       boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_app_user_username ON app_user (lower(username));