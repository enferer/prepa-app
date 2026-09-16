--liquibase formatted sql

--changeset prepa:004-cycles
CREATE TABLE cycles (
  id                    UUID PRIMARY KEY,
  athlete_id            UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  slug                  TEXT NOT NULL,
  nom                   TEXT NOT NULL,
  type                  TEXT NOT NULL,
  statut                TEXT NOT NULL,
  date_debut            DATE NOT NULL,
  date_fin              DATE NOT NULL,

  course_nom            TEXT,
  course_date           DATE,
  course_distance_m     INT,
  chrono_vise_sec       INT,

  ligne_directrice      TEXT,
  ligne_directrice_type TEXT,
  horizon_semaines      SMALLINT,

  allures_cibles        JSONB NOT NULL DEFAULT '{}'::jsonb,
  bilan                 TEXT,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT cycles_slug_unique UNIQUE (athlete_id, slug),
  CONSTRAINT cycles_type_chk CHECK (type IN ('PREPA', 'LIBRE')),
  CONSTRAINT cycles_statut_chk CHECK (statut IN ('PLANIFIE', 'ACTIF', 'TERMINE', 'ABANDONNE')),
  CONSTRAINT cycles_dates_chk CHECK (date_fin >= date_debut),
  CONSTRAINT cycles_prepa_chk CHECK (type <> 'PREPA' OR (course_date IS NOT NULL AND chrono_vise_sec IS NOT NULL)),
  CONSTRAINT cycles_libre_chk CHECK (type <> 'LIBRE' OR ligne_directrice IS NOT NULL)
);
CREATE UNIQUE INDEX idx_cycles_un_seul_actif ON cycles (athlete_id) WHERE statut = 'ACTIF';
CREATE INDEX idx_cycles_athlete ON cycles (athlete_id, date_debut DESC);
--rollback DROP TABLE cycles;
