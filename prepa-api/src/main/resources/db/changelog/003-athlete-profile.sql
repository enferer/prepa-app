--liquibase formatted sql

--changeset prepa:003-athlete-profiles
CREATE TABLE athlete_profiles (
  athlete_id          UUID PRIMARY KEY REFERENCES athletes(id) ON DELETE CASCADE,
  fc_max              SMALLINT,
  fc_repos            SMALLINT,
  vma_kmh             NUMERIC(4,1),
  volume_habituel_km  NUMERIC(5,1),
  jours_disponibles   TEXT[],
  renfo_actif         BOOLEAN NOT NULL DEFAULT false,
  renfo_frequence     SMALLINT,
  renfo_materiel      TEXT[],
  renfo_focus         TEXT,
  notes               TEXT,
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE athlete_profiles;

--changeset prepa:003-personal-records
CREATE TABLE personal_records (
  id          UUID PRIMARY KEY,
  athlete_id  UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  distance_m  INT NOT NULL,
  temps_sec   INT NOT NULL,
  date        DATE NOT NULL,
  contexte    TEXT,
  source      TEXT NOT NULL DEFAULT 'DECLARE',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT personal_records_source_chk CHECK (source IN ('DECLARE', 'ACTIVITE'))
);
CREATE INDEX idx_personal_records_athlete ON personal_records (athlete_id, distance_m, date DESC);
--rollback DROP TABLE personal_records;

--changeset prepa:003-injuries
CREATE TABLE injuries (
  id          UUID PRIMARY KEY,
  athlete_id  UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  zone        TEXT NOT NULL,
  statut      TEXT NOT NULL,
  palier      SMALLINT,
  consignes   TEXT,
  debut       DATE NOT NULL,
  fin         DATE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT injuries_statut_chk CHECK (statut IN ('ACTIVE', 'SURVEILLANCE', 'RESOLUE')),
  CONSTRAINT injuries_palier_chk CHECK (palier IS NULL OR palier BETWEEN 1 AND 3)
);
CREATE INDEX idx_injuries_athlete ON injuries (athlete_id, statut);
--rollback DROP TABLE injuries;

--changeset prepa:003-constraints
CREATE TABLE athlete_constraints (
  id          UUID PRIMARY KEY,
  athlete_id  UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  type        TEXT NOT NULL,
  debut       DATE,
  fin         DATE,
  detail      TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT athlete_constraints_type_chk CHECK (type IN ('VACANCES', 'METEO', 'MATERIEL', 'PRO', 'AUTRE'))
);
CREATE INDEX idx_constraints_athlete ON athlete_constraints (athlete_id);
--rollback DROP TABLE athlete_constraints;

--changeset prepa:003-signature-sessions
CREATE TABLE signature_sessions (
  id                  UUID PRIMARY KEY,
  athlete_id          UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  nom                 TEXT NOT NULL,
  type_seance         TEXT NOT NULL,
  description         TEXT,
  distance_km         NUMERIC(5,2),
  frequence_souhaitee TEXT,
  contexte            TEXT,
  actif               BOOLEAN NOT NULL DEFAULT true,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_signature_sessions_athlete ON signature_sessions (athlete_id, actif);
--rollback DROP TABLE signature_sessions;
