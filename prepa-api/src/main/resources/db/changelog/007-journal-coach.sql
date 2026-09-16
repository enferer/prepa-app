--liquibase formatted sql

--changeset prepa:007-journal-entries
CREATE TABLE journal_entries (
  id          UUID PRIMARY KEY,
  athlete_id  UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  date        DATE NOT NULL,
  contenu     TEXT NOT NULL,
  humeur      SMALLINT,
  fatigue     SMALLINT,
  sommeil_h   NUMERIC(3,1),
  douleur     BOOLEAN NOT NULL DEFAULT false,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT journal_entries_date_unique UNIQUE (athlete_id, date),
  CONSTRAINT journal_entries_humeur_chk CHECK (humeur IS NULL OR humeur BETWEEN 1 AND 5),
  CONSTRAINT journal_entries_fatigue_chk CHECK (fatigue IS NULL OR fatigue BETWEEN 1 AND 5)
);
CREATE INDEX idx_journal_entries_athlete ON journal_entries (athlete_id, date DESC);
--rollback DROP TABLE journal_entries;

--changeset prepa:007-coach-notes
CREATE TABLE coach_notes (
  id          UUID PRIMARY KEY,
  athlete_id  UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  cycle_id    UUID REFERENCES cycles(id) ON DELETE SET NULL,
  date        DATE NOT NULL,
  portee      TEXT NOT NULL,
  categorie   TEXT NOT NULL,
  titre       TEXT NOT NULL,
  contenu     TEXT NOT NULL,
  actif       BOOLEAN NOT NULL DEFAULT true,
  remplace_id UUID REFERENCES coach_notes(id) ON DELETE SET NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT coach_notes_portee_chk CHECK (portee IN ('DURABLE', 'CYCLE', 'PONCTUELLE')),
  CONSTRAINT coach_notes_categorie_chk CHECK (categorie IN ('DECISION', 'OBSERVATION', 'CONSIGNE', 'ALERTE')),
  CONSTRAINT coach_notes_titre_len_chk CHECK (char_length(titre) <= 80),
  CONSTRAINT coach_notes_contenu_len_chk CHECK (char_length(contenu) <= 500)
);
CREATE INDEX idx_coach_notes_lookup ON coach_notes (athlete_id, portee, actif, date DESC);
--rollback DROP TABLE coach_notes;

--changeset prepa:007-weekly-reports
CREATE TABLE weekly_reports (
  id                UUID PRIMARY KEY,
  athlete_id        UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  cycle_id          UUID NOT NULL REFERENCES cycles(id) ON DELETE CASCADE,
  week_id           UUID REFERENCES training_weeks(id) ON DELETE SET NULL,
  date_debut        DATE NOT NULL,
  bilan             TEXT NOT NULL,
  points_attention  TEXT,
  changements       JSONB,
  consignes         TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT weekly_reports_unique UNIQUE (cycle_id, date_debut)
);
CREATE INDEX idx_weekly_reports_cycle ON weekly_reports (cycle_id, date_debut DESC);
--rollback DROP TABLE weekly_reports;
