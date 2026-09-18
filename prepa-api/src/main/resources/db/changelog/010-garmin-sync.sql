--liquibase formatted sql

--changeset prepa:010-garmin-jetons
-- Les jetons Garmin vivaient dans un volume Docker cote worker. Ils reviennent en base,
-- chiffres comme les identifiants : ils entrent ainsi dans la sauvegarde, et l'API n'a plus
-- besoin d'un systeme de fichiers persistant. Le jeton OAuth1 vaut environ un an, l'OAuth2
-- une heure — c'est ce qui evite de rejouer le mot de passe, et donc de declencher la 2FA.
ALTER TABLE garmin_credentials
  ADD COLUMN oauth1_token_enc  BYTEA,
  ADD COLUMN oauth1_secret_enc BYTEA,
  ADD COLUMN oauth2_token_enc  BYTEA,
  ADD COLUMN oauth2_expire_at  TIMESTAMPTZ,
  ADD COLUMN mfa_contexte_enc  BYTEA,
  ADD COLUMN mfa_demande_at    TIMESTAMPTZ;
--rollback ALTER TABLE garmin_credentials DROP COLUMN oauth1_token_enc, DROP COLUMN oauth1_secret_enc, DROP COLUMN oauth2_token_enc, DROP COLUMN oauth2_expire_at, DROP COLUMN mfa_contexte_enc, DROP COLUMN mfa_demande_at;

--changeset prepa:010-garmin-statuts
-- EN_COURS parce que le passage est desormais asynchrone et observable pendant qu'il tourne ;
-- MFA_REQUISE parce qu'il n'y a plus de terminal sur le serveur pour saisir un code.
ALTER TABLE garmin_credentials DROP CONSTRAINT garmin_credentials_statut_chk;
ALTER TABLE garmin_credentials ADD CONSTRAINT garmin_credentials_statut_chk
  CHECK (dernier_statut IS NULL OR dernier_statut IN
    ('OK', 'EN_COURS', 'AUTH_ERROR', 'IDENTITE_KO', 'MFA_REQUISE', 'ERREUR'));
--rollback ALTER TABLE garmin_credentials DROP CONSTRAINT garmin_credentials_statut_chk; ALTER TABLE garmin_credentials ADD CONSTRAINT garmin_credentials_statut_chk CHECK (dernier_statut IS NULL OR dernier_statut IN ('OK', 'AUTH_ERROR', 'IDENTITE_KO', 'ERREUR'));

--changeset prepa:010-garmin-sync-runs
-- Une ligne par athlete et par passage. Les colonnes dernier_* de garmin_credentials ne
-- gardent que l'instant present : pour savoir ce qui s'est passe la nuit derniere, il fallait
-- lire les journaux du conteneur. Cette table-ci rend l'exploitation consultable.
CREATE TABLE garmin_sync_runs (
  id           UUID PRIMARY KEY,
  athlete_id   UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  demarre_a    TIMESTAMPTZ NOT NULL,
  termine_a    TIMESTAMPTZ,
  declencheur  TEXT NOT NULL,
  demande_par  TEXT,
  fenetre_du   DATE,
  fenetre_au   DATE,
  statut       TEXT NOT NULL,
  message      TEXT,
  recues       INT,
  importees    INT,
  mises_a_jour INT,
  doublons     INT,
  CONSTRAINT garmin_sync_runs_declencheur_chk CHECK (declencheur IN
    ('PLANIFIE', 'DEMANDE', 'MANUEL')),
  CONSTRAINT garmin_sync_runs_statut_chk CHECK (statut IN
    ('EN_COURS', 'OK', 'AUTH_ERROR', 'IDENTITE_KO', 'MFA_REQUISE', 'ERREUR'))
);
CREATE INDEX garmin_sync_runs_athlete_idx ON garmin_sync_runs (athlete_id, demarre_a DESC);
CREATE INDEX garmin_sync_runs_demarre_idx ON garmin_sync_runs (demarre_a DESC);
--rollback DROP TABLE garmin_sync_runs;
