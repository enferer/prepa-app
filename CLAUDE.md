# prepa-app

Suivi d'entraînement en course à pied, **multi-athlètes**, avec une dimension coach. Une
application web adossée à une API et à une base Postgres ; le coach, lui, reste Claude Code,
qui lit et écrit par cette API.

## Le modèle : des cycles, pas seulement des prépas

L'unité de travail est le **cycle** — une période d'entraînement continue avec une intention.
Il a deux formes :

- **`PREPA`** — une course à une date, un chrono visé, un plan complet jusqu'au jour J.
- **`LIBRE`** — pas de course en vue : une **ligne directrice** (« maintenir la charge »,
  « progresser en VO2max », « passer au trail ») et un **horizon** en semaines.

Même structure de semaines et de séances dans les deux cas. Ce qui diffère, c'est la
planification : en cycle libre, le plan est **glissant** — trois à quatre semaines détaillées
séance par séance, le reste en cibles hebdomadaires jusqu'à l'horizon. Planifier une séance à
deux mois sans échéance qui l'impose serait une fausse précision.

Un athlète a **au plus un cycle actif**. Les activités, elles, sont rattachées à l'athlète et
traversent les cycles : c'est ce qui permet de comparer une période à une autre.

## Structure

```
prepa-app/
├── coach/COACH.md          la méthodologie — fait autorité
├── prepa-api/              API Spring Boot 4 / Java 21
├── prepa-web/              application Vue 3
├── worker/                 synchronisation Garmin (Python)
├── cli/prepa               client des skills
├── migration/              import depuis l'ancienne application
├── exploitation/           sauvegarde, restauration, passage nocturne
└── .claude/skills/         prepa-cycle, prepa-update
```

## Rôle coach — important

Pour **toute question d'entraînement, d'allure, d'adaptation ou de comportement de coach**,
lis d'abord [`coach/COACH.md`](coach/COACH.md). C'est le cerveau du projet : principes,
calcul des allures, règles d'adaptation, protocole douleur, cycles libres, et les cinq
situations qu'on ne traite **jamais** sans demander la cause à l'athlète.

## Le cycle de vie d'une séance

Trois temps, et la distinction tient à **qui pose le statut** :

| Statut | Posé par | Quand |
|---|---|---|
| `A_VENIR` | — | par défaut |
| `REALISEE` | **le système** | une activité Garmin correspond à la séance |
| `NON_REALISEE` | **le système** | la date est passée, rien n'est venu (un jour de battement) |
| `ANALYSEE` | **le coach** | il l'a regardée et commentée |
| `DEPLACEE` / `ANNULEE` | selon le cas | reportée dans la semaine, ou retirée du plan |

`REALISEE` et `NON_REALISEE` sont des **constats**, posés à l'ingestion : l'athlète voit sa
sortie comptée le soir même, sans attendre le point hebdomadaire, et le coach n'a pas à
cocher à la main ce que les données disent déjà. `ANALYSEE` est un **jugement**.

Il n'existe volontairement pas de statut « manquée » automatique : déclarer une séance
manquée avant d'avoir demandé à l'athlète ce qui s'est passé reviendrait à juger sans savoir.
Le constat dit qu'il ne s'est rien passé ; la raison vit dans le commentaire du coach.

Un constat n'écrase jamais une décision déjà prise : une séance analysée, déplacée ou annulée
ne bouge plus.

## Qui écrit quoi

La ligne de partage est portée par le code, pas par une convention : *l'athlète décrit la
réalité, il ne redessine pas l'entraînement.*

| | Athlète (application) | Coach (clé de service) |
|---|---|---|
| Marquer une séance faite ou non faite (sortie sans montre) | ✅ | ✅ |
| Commenter une séance, tenir son journal | ✅ | ✅ (commentaire de coach) |
| Décaler une séance **dans sa semaine** | ✅ | ✅ (n'importe où) |
| Saisir un ressenti, signaler une blessure | ✅ | ✅ |
| Type, allures, distance cible d'une séance | ❌ | ✅ |
| Ajouter ou supprimer une séance, cibles hebdo | ❌ | ✅ |
| Créer, convertir, clôturer un cycle | ❌ | ✅ |
| Écrire une note de coach | ❌ | ✅ |

Une tentative interdite renvoie `403 FORBIDDEN_FIELD` **en nommant le champ**, plutôt qu'un
refus opaque.

## Développement

```bash
docker compose up -d                    # Postgres sur :5433
cd prepa-api && ./mvnw spring-boot:run  # API sur :8080
cd prepa-web && npm run dev             # application sur :5173
```

Démarrage détaillé, comptes et import des données : [DEMARRAGE.md](DEMARRAGE.md).

```bash
cd prepa-api && ./mvnw test     # Postgres réel via Testcontainers
cd prepa-web && npm run build   # vérifie aussi les types
```

## Les skills

Deux, et deux seulement :

- **`/prepa-cycle`** — ouvrir un cycle, basculer de libre à prépa, clôturer.
- **`/prepa-update`** — le point de la semaine, à lancer chaque semaine. Rapatrie les
  dernières séances, lit le contexte et les écarts déjà qualifiés, questionne, adapte,
  enregistre le bilan.

Le sync Garmin n'a plus son propre skill : il tourne seul chaque nuit sur le serveur, et
`/prepa-update` le déclenche au démarrage si l'athlète a couru entre-temps.

Ils passent par `cli/prepa` ([documentation](cli/README.md)), configuré dans
`~/.prepa-cli/config.json`.

## Endpoints qui portent le métier

| Endpoint | Ce qu'il donne |
|---|---|
| `GET /athletes/{id}/coach-context` | l'état de l'athlète en un appel **borné** — profil, blessures, cycle, semaine, mémoire du coach, journal récent |
| `GET /athletes/{id}/reconciliation` | prévu contre réalisé, **écarts déjà qualifiés** |
| `GET /athletes/{id}/analysis` | volume hebdo, allure d'endurance réelle, meilleurs efforts, tendances |
| `GET /athletes/{id}/activities/new` | séances jamais passées en revue, bornées au cycle |
| `GET /cycles/{id}/skeleton` | trame de charge d'un cycle libre |

## La mémoire du coach

Les décisions deviennent des `coach_notes` **datées, typées et bornées** — plus un champ de
commentaires qui grossit sans fin. Trois portées : `DURABLE` (règle permanente), `CYCLE`
(vraie pour cette préparation), `PONCTUELLE` (purgée après huit semaines). Contenu plafonné à
**500 caractères** : une décision qui n'y tient pas est mal formulée. Une décision qui en
annule une autre la désactive explicitement, plutôt que de laisser deux consignes
contradictoires.

## Synchronisation Garmin

Le worker tourne sur le serveur, en continu pour les demandes immédiates et en passage
nocturne pour le reste. Les identifiants sont chiffrés au repos (AES-GCM) et ne ressortent
que pour lui. **Garde-fou d'identité** : au premier passage réussi, le compte Garmin est
mémorisé sur l'athlète ; aux suivants, un compte différent arrête la synchronisation au lieu
d'écrire les séances d'un athlète dans l'historique d'un autre.

## Déploiement

Voir [DEPLOIEMENT.md](DEPLOIEMENT.md).

