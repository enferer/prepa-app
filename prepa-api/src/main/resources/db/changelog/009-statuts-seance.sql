--liquibase formatted sql

--changeset prepa:009-statuts-seance
-- Les statuts d'une seance distinguent desormais le constat du jugement.
--
-- VALIDEE disait deux choses a la fois : « l'activite est la » et « le coach l'a regardee ».
-- Confondues, elles obligeaient a attendre le point hebdomadaire pour qu'une sortie soit
-- comptee. Une seance deja validee a bien ete passee en revue : elle devient ANALYSEE.
-- Une seance manquee devient le constat NON_REALISEE — la raison, elle, vit dans le
-- commentaire du coach.
ALTER TABLE planned_sessions DROP CONSTRAINT planned_sessions_statut_chk;

UPDATE planned_sessions SET statut = 'ANALYSEE'     WHERE statut = 'VALIDEE';
UPDATE planned_sessions SET statut = 'NON_REALISEE' WHERE statut = 'MANQUEE';

ALTER TABLE planned_sessions ADD CONSTRAINT planned_sessions_statut_chk CHECK (statut IN
  ('A_VENIR', 'REALISEE', 'NON_REALISEE', 'ANALYSEE', 'DEPLACEE', 'ANNULEE'));

--rollback ALTER TABLE planned_sessions DROP CONSTRAINT planned_sessions_statut_chk;
--rollback UPDATE planned_sessions SET statut = 'VALIDEE' WHERE statut IN ('ANALYSEE', 'REALISEE');
--rollback UPDATE planned_sessions SET statut = 'MANQUEE' WHERE statut = 'NON_REALISEE';
--rollback ALTER TABLE planned_sessions ADD CONSTRAINT planned_sessions_statut_chk CHECK (statut IN
--rollback   ('A_VENIR', 'VALIDEE', 'MANQUEE', 'DEPLACEE', 'ANNULEE'));
