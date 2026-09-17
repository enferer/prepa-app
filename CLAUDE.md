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
└── .claude/skills/         prepa-cycle, prepa-update, prepa-sync
```

## Rôle coach — important

Pour **toute question d'entraînement, d'allure, d'adaptation ou de comportement de coach**,
lis d'abord [`coach/COACH.md`](coach/COACH.md). C'est le cerveau du projet : principes,
calcul des allures, règles d'adaptation, protocole douleur, cycles libres, et les cinq
situations qu'on ne traite **jamais** sans demander la cause à l'athlète.

## Qui écrit quoi

La ligne de partage est portée par le code, pas par une convention : *l'athlète décrit la
réalité, il ne redessine pas l'entraînement.*

| | Athlète (application) | Coach (clé de service) |
|---|---|---|
| Marquer une séance faite ou manquée | ✅ | ✅ |
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
docker compose up -d                              # Postgres local
cd prepa-api && ./mvnw spring-boot:run            # API sur :8080
cd prepa-web && npm run dev                       # application sur :5173
```

Au premier démarrage, `--prepa.seed.enabled=true` avec `prepa.seed.admin-email` et
`prepa.seed.admin-password` crée le compte administrateur et affiche **une seule fois** une
clé de service.

```bash
cd prepa-api && ./mvnw test     # Postgres réel via Testcontainers
cd prepa-web && npm run build   # vérifie aussi les types
```

## Les skills

- **`/prepa-cycle`** — ouvrir, convertir ou clôturer un cycle.
- **`/prepa-update`** — le point de la semaine. Lit le contexte et les écarts déjà qualifiés,
  questionne, adapte, enregistre le bilan.
- **`/prepa-sync`** — forcer une synchronisation Garmin. N'analyse rien.

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

