--liquibase formatted sql

--changeset prepa:012-username
-- Un athlete se connecte desormais par son email ou par son nom d'utilisateur.
--
-- L'email identifie la personne, le nom d'utilisateur identifie le compte : il se tape plus
-- vite sur un telephone au bord de la piste, et ne change pas quand on change de messagerie.
-- Les deux menent au meme athlete, la colonne est donc obligatoire et unique comme l'email.
--
-- Pour les comptes deja ouverts, on la derive du nom affiche plutot que de laisser le champ
-- vide : « Thomas D. » devient « thomas-d ». Deux noms qui donnent le meme resultat sont
-- departages par un suffixe numerique, et un nom sans aucune lettre latine retombe sur
-- l'identifiant technique. L'admin corrige ensuite ce qui merite de l'etre.
alter table athletes add column username text;

update athletes cible
set username = derive.candidat
from (
    select id,
           coalesce(slug, 'athlete-' || substr(id::text, 1, 8))
               || case when rang = 1 then '' else '-' || rang end as candidat
    from (
        select id,
               slug,
               row_number() over (partition by slug order by created_at, id) as rang
        from (
            select id,
                   created_at,
                   nullif(
                       trim(both '-' from regexp_replace(
                           translate(
                               lower(display_name),
                               'àâäáãåçéèêëíìîïñóòôöõúùûüýÿ',
                               'aaaaaaceeeeiiiinooooouuuuyy'),
                           '[^a-z0-9]+', '-', 'g')),
                       '') as slug
            from athletes
        ) nettoye
    ) numerote
) derive
where cible.id = derive.id;

alter table athletes alter column username set not null;

alter table athletes add constraint athletes_username_chk
    check (username ~ '^[a-z0-9][a-z0-9._-]{0,29}$');

create unique index idx_athletes_username on athletes (lower(username));

comment on column athletes.username is
    'Identifiant de connexion alternatif a l''email. Pose par un admin, unique a la casse pres.';
--rollback DROP INDEX idx_athletes_username;
--rollback ALTER TABLE athletes DROP CONSTRAINT athletes_username_chk;
--rollback ALTER TABLE athletes DROP COLUMN username;
