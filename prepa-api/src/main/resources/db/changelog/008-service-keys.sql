--liquibase formatted sql

--changeset prepa:008-service-keys
CREATE TABLE service_keys (
  id            UUID PRIMARY KEY,
  nom           TEXT NOT NULL,
  key_hash      TEXT NOT NULL UNIQUE,
  scopes        TEXT[] NOT NULL,
  athlete_id    UUID REFERENCES athletes(id) ON DELETE CASCADE,
  last_used_at  TIMESTAMPTZ,
  revoked_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE service_keys;

--changeset prepa:008-garmin-credentials
CREATE TABLE garmin_credentials (
  athlete_id      UUID PRIMARY KEY REFERENCES athletes(id) ON DELETE CASCADE,
  email_enc       BYTEA NOT NULL,
  password_enc    BYTEA NOT NULL,
  derniere_sync   TIMESTAMPTZ,
  dernier_statut  TEXT,
  dernier_message TEXT,
  sync_demande    BOOLEAN NOT NULL DEFAULT false,
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT garmin_credentials_statut_chk CHECK (dernier_statut IS NULL OR dernier_statut IN
    ('OK', 'AUTH_ERROR', 'IDENTITE_KO', 'ERREUR'))
);
--rollback DROP TABLE garmin_credentials;
