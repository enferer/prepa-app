--liquibase formatted sql

--changeset prepa:002-athletes
CREATE TABLE athletes (
  id                  UUID PRIMARY KEY,
  email               TEXT NOT NULL,
  password_hash       TEXT NOT NULL,
  display_name        TEXT NOT NULL,
  timezone            TEXT NOT NULL DEFAULT 'Europe/Paris',
  locale              TEXT NOT NULL DEFAULT 'fr-FR',
  role                TEXT NOT NULL DEFAULT 'ATHLETE',
  garmin_display_name TEXT,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT athletes_role_chk CHECK (role IN ('ATHLETE', 'ADMIN'))
);
CREATE UNIQUE INDEX idx_athletes_email ON athletes (lower(email));
--rollback DROP TABLE athletes;

--changeset prepa:002-refresh-tokens
CREATE TABLE refresh_tokens (
  id          UUID PRIMARY KEY,
  athlete_id  UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  token_hash  TEXT NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_athlete ON refresh_tokens (athlete_id);
--rollback DROP TABLE refresh_tokens;
