--liquibase formatted sql

--changeset prepa:005-activities
CREATE TABLE activities (
  id                  UUID PRIMARY KEY,
  athlete_id          UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  garmin_activity_id  BIGINT,
  dedup_key           TEXT NOT NULL,
  source              TEXT NOT NULL,
  type                TEXT NOT NULL,
  type_garmin         TEXT,
  titre               TEXT,
  started_at          TIMESTAMPTZ NOT NULL,
  date_locale         DATE NOT NULL,
  duree_sec           INT NOT NULL,
  duree_mouvement_sec INT,
  distance_m          INT,
  allure_moy_sec_km   INT,
  meilleure_allure_sec_km INT,
  gap_moy_sec_km      INT,
  fc_moy              SMALLINT,
  fc_max              SMALLINT,
  fc_min              SMALLINT,
  cadence_moy         SMALLINT,
  cadence_max         SMALLINT,
  denivele_pos_m      INT,
  denivele_neg_m      INT,
  altitude_min_m      INT,
  altitude_max_m      INT,
  calories            INT,
  te_aerobie          NUMERIC(3,1),
  te_anaerobie        NUMERIC(3,1),
  te_label            TEXT,
  charge_entrainement NUMERIC(7,1),
  vo2max              NUMERIC(4,1),
  longueur_foulee_m   NUMERIC(4,2),
  oscillation_verticale NUMERIC(4,1),
  temps_contact_sol   SMALLINT,
  puissance_moy       INT,
  puissance_max       INT,
  lieu                TEXT,
  rpe                 SMALLINT,
  ressenti            TEXT,
  a_detail            BOOLEAN NOT NULL DEFAULT false,
  meteo               JSONB,
  zones_fc            JSONB,
  imported_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT activities_dedup_unique UNIQUE (athlete_id, dedup_key),
  CONSTRAINT activities_source_chk CHECK (source IN ('GARMIN_API', 'CSV_IMPORT', 'MANUELLE')),
  CONSTRAINT activities_rpe_chk CHECK (rpe IS NULL OR rpe BETWEEN 1 AND 10)
);
CREATE UNIQUE INDEX idx_activities_garmin_id ON activities (athlete_id, garmin_activity_id)
  WHERE garmin_activity_id IS NOT NULL;
CREATE INDEX idx_activities_athlete_date ON activities (athlete_id, started_at DESC);
CREATE INDEX idx_activities_athlete_locale ON activities (athlete_id, date_locale);
--rollback DROP TABLE activities;

--changeset prepa:005-activity-laps
CREATE TABLE activity_laps (
  id              UUID PRIMARY KEY,
  activity_id     UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
  index_tour      SMALLINT NOT NULL,
  distance_m      INT,
  duree_sec       INT NOT NULL,
  allure_sec_km   INT,
  gap_sec_km      INT,
  fc_moy          SMALLINT,
  fc_max          SMALLINT,
  cadence_moy     SMALLINT,
  puissance_moy   INT,
  denivele_pos_m  INT,
  denivele_neg_m  INT,
  intensite       TEXT,
  CONSTRAINT activity_laps_unique UNIQUE (activity_id, index_tour)
);
CREATE INDEX idx_activity_laps_activity ON activity_laps (activity_id, index_tour);
--rollback DROP TABLE activity_laps;

--changeset prepa:005-analysis-state
CREATE TABLE analysis_state (
  activity_id   UUID PRIMARY KEY REFERENCES activities(id) ON DELETE CASCADE,
  athlete_id    UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  analysee_le   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_analysis_state_athlete ON analysis_state (athlete_id);
--rollback DROP TABLE analysis_state;
