--liquibase formatted sql

--changeset prepa:011-structure-seance
-- Le deroule d'une seance devient une donnee, au lieu d'etre relu dans sa description.
--
-- « 2 km ech. + 3x1 km a AM recup 2 min + 2 km RAC » est une phrase : elle se lit bien, mais
-- la dessiner suppose de la comprendre, et une lecture de langage naturel se trompe tot ou
-- tard. Le coach peut desormais poser directement les blocs qu'il a en tete.
--
-- La colonne est nullable a dessein : les seances deja ecrites gardent leur description pour
-- seule source, et c'est le parser qui les dessine. Aucune reprise de donnees n'est donc
-- necessaire — le plan se nettoie de lui-meme, semaine apres semaine.
alter table planned_sessions add column structure jsonb;

comment on column planned_sessions.structure is
    'Deroule pose par le coach : liste de blocs {role, repetitions, distanceKm, dureeSec, allureSecKm, recupSec}. Null = deduire de la description.';
