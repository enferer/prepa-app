--liquibase formatted sql

--changeset prepa:001-extensions
-- Aucune extension requise : l'unicite d'email insensible a la casse passe par un index
-- fonctionnel sur lower(email) plutot que par citext, que la validation de schema Hibernate
-- ne sait pas reconnaitre.
SELECT 1;
--rollback SELECT 1;
