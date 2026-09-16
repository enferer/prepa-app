--liquibase formatted sql

--changeset prepa:006-training-weeks
CREATE TABLE training_weeks (
  id                UUID PRIMARY KEY,
  cycle_id          UUID NOT NULL REFERENCES cycles(id) ON DELETE CASCADE,
  numero            SMALLINT NOT NULL,
  date_debut        DATE NOT NULL,
  bloc              TEXT,
  volume_cible_km   NUMERIC(5,1) NOT NULL,
  nb_qualite_cible  SMALLINT,
  denivele_cible_m  INT,
  detaillee         BOOLEAN NOT NULL DEFAULT true,
  note              TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT training_weeks_numero_unique UNIQUE (cycle_id, numero),
  CONSTRAINT training_weeks_bloc_chk CHECK (bloc IS NULL OR bloc IN
    ('BASE', 'DEVELOPPEMENT', 'SPECIFIQUE', 'AFFUTAGE', 'DECHARGE', 'LIBRE', 'REPRISE'))
);
CREATE INDEX idx_training_weeks_cycle ON training_weeks (cycle_id, date_debut);
--rollback DROP TABLE training_weeks;

--changeset prepa:006-planned-sessions
CREATE TABLE planned_sessions (
  id                    UUID PRIMARY KEY,
  week_id               UUID NOT NULL REFERENCES training_weeks(id) ON DELETE CASCADE,
  cycle_id              UUID NOT NULL REFERENCES cycles(id) ON DELETE CASCADE,
  date                  DATE NOT NULL,
  ordre                 SMALLINT NOT NULL DEFAULT 0,
  type                  TEXT NOT NULL,
  titre                 TEXT NOT NULL,
  description           TEXT,
  statut                TEXT NOT NULL DEFAULT 'A_VENIR',
  allures_texte         TEXT,
  distance_cible_km     NUMERIC(5,2),
  duree_cible_min       SMALLINT,
  focus                 TEXT,
  commentaire_coach     TEXT,
  commentaire_athlete   TEXT,
  signature_session_id  UUID REFERENCES signature_sessions(id) ON DELETE SET NULL,
  activity_id           UUID REFERENCES activities(id) ON DELETE SET NULL,
  rapprochement         TEXT NOT NULL DEFAULT 'AUCUN',
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT planned_sessions_type_chk CHECK (type IN
    ('EF', 'SL', 'SEUIL', 'VMA', 'AM', 'COTES', 'RENFO', 'COURSE', 'CROSS', 'REPOS')),
  CONSTRAINT planned_sessions_statut_chk CHECK (statut IN
    ('A_VENIR', 'VALIDEE', 'MANQUEE', 'DEPLACEE', 'ANNULEE')),
  CONSTRAINT planned_sessions_rappr_chk CHECK (rapprochement IN ('AUTO', 'MANUEL', 'AUCUN')),
  CONSTRAINT planned_sessions_renfo_focus_chk CHECK (type <> 'RENFO' OR focus IS NOT NULL)
);
CREATE INDEX idx_planned_sessions_cycle_date ON planned_sessions (cycle_id, date);
CREATE INDEX idx_planned_sessions_week ON planned_sessions (week_id, date, ordre);
CREATE UNIQUE INDEX idx_planned_sessions_activity ON planned_sessions (activity_id)
  WHERE activity_id IS NOT NULL;
--rollback DROP TABLE planned_sessions;
