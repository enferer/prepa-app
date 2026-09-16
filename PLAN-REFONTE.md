# Refonte `prepa-app` → application web + base de données

> Document de cadrage destiné à être repris **dans un projet vierge**. Il est autoportant : il décrit le pourquoi, le modèle fonctionnel, le modèle de données, l'API, le front, les skills et la migration.

---

## 1. Contexte

### D'où on part

`prepa-app` est aujourd'hui un outil **local, fichiers-first, piloté par Claude Code** :

- Les données vivent dans `profiles/<athlète>/prepas/<slug>/data/` : `garmin.csv`, `plan.json`, `objectifs.json`, `journal.md`, plus des dérivés générés (`activites.json`, `detail_seances.json`, `garmin_raw/*.json`).
- La « vue » est une page statique ouverte en `file://`, alimentée par un `vue/data.js` **généré de 1,14 Mo** qui contient le catalogue de tous les profils. Le rendu est un seul `app.js` de 1 446 lignes en `innerHTML`.
- Le coach, c'est **Claude Code lui-même**, appliquant `coach/COACH.md` via trois skills (`/prepa-init`, `/prepa-sync`, `/prepa-update`).

Deux athlètes l'utilisent (thibaut, camille), tous deux sur un marathon au 25/10/2026.

### Ce qui ne va plus

1. **Rien n'existe hors de la machine.** Pas de consultation depuis le téléphone, pas de sauvegarde autre que git, data régénérée à chaque build.
2. **Tout est recalculé à chaque fois** : `build_data.py` reparse l'intégralité du CSV et réécrit 1,14 Mo pour ajouter une séance.
3. **Le modèle force une course.** Une prépa = une course avec une date. Après le marathon, l'outil n'a plus rien à dire.
4. **Les dérivés sont fragiles** : le lien activité ↔ détail repose sur une égalité de `dateHeure` à la seconde, faute d'`activityId` dans le CSV Garmin.
5. **La mémoire du coach est un pavé** : `objectifs.commentairesLibres` cumule ~6 000 caractères de décisions chez thibaut, relu intégralement à chaque update.

### Ce qu'on veut

- Une **app Vue 3** propre, consultable depuis n'importe où (mobile responsive), alimentée par une **API** et une **base Postgres hébergée sur un VPS**.
- Un **suivi d'entraînement continu**, dont la prépa n'est qu'un mode parmi deux : on peut enchaîner un cycle « libre » guidé par une ligne directrice (« maintenir la charge », « progresser en VO2max », « passer au trail »), puis rebasculer en prépa quand une course est choisie.
- Le **coach reste Claude Code** : les skills lisent et écrivent par l'API. Pas de LLM dans l'application.
- Le **sync Garmin tourne tout seul** sur le VPS, en cron nocturne, pour tous les athlètes.

### Rapport au `cadrage-refonte/` existant

Le repo contient déjà `cadrage-refonte/` (11 documents), qui vise un **SaaS public** : inscription ouverte, chat coach LLM côté back, `PromptBuilder`/`TokenBudget`, quotas, Stripe, OAuth Garmin officiel.

**Ce plan-ci est volontairement plus resserré** : outil personnel multi-athlètes, coach = Claude Code, pas de LLM serveur, pas de facturation. On **reprend** du cadrage ce qui reste valable (stack Java 21 / Spring Boot 3.3 / Postgres 16 / Vue 3 + Vite + Pinia + Tailwind, conventions REST, enveloppe d'erreur, Liquibase, Testcontainers) et on **écarte** pour l'instant tout le pan LLM/quotas/billing. Les tables `llm_calls`, `usage_ledger`, `subscriptions`, `chat_*` ne sont **pas** créées.

Les documents du cadrage restent utiles comme référence si le projet s'ouvre plus tard — mais ne pas les implémenter maintenant.

### Ce qui fait autorité et doit être porté tel quel

- **`coach/COACH.md`** — la méthodologie (80/20, +10 %/semaine, décharge toutes les 3-4 semaines, SL plafonnée 30-32 km, dérivation des allures depuis l'AM, protocole douleur en 3 paliers, déclencheurs de question obligatoire). C'est le cerveau. Il est recopié dans le nouveau projet et **reste le brief des skills**, enrichi de la section « cycle libre » (§3.4).
- **`scripts/build_data.py`** — la normalisation du CSV Garmin (mapping des 30 en-têtes FR, `to_float` avec séparateurs FR/US, `duration_to_seconds`, `pace_to_seconds`, `parse_date` sur 6 formats) est la **spécification de référence** du parsing. Elle est portée telle quelle.
- **`scripts/details.py`** — extraction des tours (filtre des tours artefacts `< 10 s` **et** `< 50 m`), zones FC, et **conversion impériale → métrique de la météo** (`(°F−32)×5/9`, `mph×1,609`), que l'API Garmin sert en impérial quelle que soit la locale.
- **`vue/js/app.js`** — les calculs dérivés à porter côté back : `grouperEnBlocs` (regroupement des tours consécutifs de même intensité, FC pondérée par la durée), `estStructuree`, assiduité hors renfo, volume réalisé par fenêtre de 7 jours, jointure plan ↔ activités par date avec appariement sur la distance cible.

---

## 2. Le changement fonctionnel : le Cycle

C'est le cœur de la refonte. On remplace le couple « profil → prépa » par **athlète → cycles successifs**.

### 2.1 Définition

Un **cycle** est une période d'entraînement continue avec une intention. Il a un **type** :

| | `PREPA` | `LIBRE` |
|---|---|---|
| Intention | Une course à une date, un chrono visé | Une **ligne directrice** en une phrase |
| Fin | La date de la course | Un **horizon** choisi à la création (« libre sur 3 mois ») |
| Planification | Plan complet jusqu'à la course, structuré en blocs | **Plan glissant** sur l'horizon, régénéré/prolongé à chaque `/prepa-update` |
| Cibles | Allures dérivées du chrono visé | Cibles hebdo (volume, nb de séances qualité, D+) + allures dérivées des références actuelles |

**Même structure de semaines et de séances dans les deux cas** — c'est ce qui rend l'UI et les skills uniformes. Seule la façon de générer le plan diffère.

### 2.2 Cycle actif et transitions

- Un athlète a **au plus un cycle actif** à la fois (contrainte d'unicité partielle en base).
- Les cycles sont **contigus** : la fin de l'un précède le début du suivant. L'historique d'activités, lui, est **rattaché à l'athlète**, pas au cycle — c'est ce qui permet les comparaisons « avant / pendant » et le suivi longue durée.
- Transitions offertes :
  - **Clôturer un cycle** → bilan de cycle généré par le coach, cycle passé en `TERMINE`.
  - **Démarrer un cycle libre** → ligne directrice + horizon en semaines + jours disponibles.
  - **Passer en prépa** → course, date, chrono visé ; le coach vérifie que l'horizon restant est suffisant et le challenge si non (règle §3 COACH.md).
  - **Prolonger un cycle libre** → on repousse l'horizon, le plan glissant continue.

### 2.3 Le plan glissant en mode libre

En `LIBRE`, l'horizon est déclaré à la création (ex. 12 semaines), mais le plan n'est **pas figé** sur toute la durée :

- Le coach génère **les 3-4 prochaines semaines en détail** (séances datées).
- Au-delà, seules des **cibles hebdomadaires** existent (`volume_cible_km`, `nb_qualite_cible`, `denivele_cible_m`) jusqu'à la fin de l'horizon — elles servent la progressivité (+10 %/sem, décharge toutes les 3-4 semaines) sans prétendre planifier une séance dans 2 mois.
- Chaque `/prepa-update` **détaille une semaine de plus** et ajuste les cibles restantes.

En `PREPA`, le plan est détaillé jusqu'à la course dès la génération, comme aujourd'hui.

### 2.4 Lignes directrices — enum ouvert

`ligne_directrice_type` : `MAINTIEN_CHARGE` · `VO2MAX` · `ENDURANCE_FONDAMENTALE` · `TRAIL_DENIVELE` · `VITESSE_COURTE` · `REPRISE_POST_COURSE` · `RETOUR_BLESSURE` · `AUTRE`, accompagné d'un champ texte libre `ligne_directrice` qui reste la formulation de l'athlète.

Le type sert au coach à choisir la **répartition des séances** (`COACH.md` §libre, à écrire) ; le texte libre sert au dialogue. Chaque type définit une trame indicative :

| Type | Trame |
|---|---|
| `MAINTIEN_CHARGE` | Volume stable au niveau de fin de prépa, 1 qualité + 1 sortie longue/sem, 80/20 |
| `VO2MAX` | Volume −15 %, 2 séances VMA/sem (intervalles courts 30/30, 400-1000 m), SL raccourcie |
| `TRAIL_DENIVELE` | Cible de D+ hebdo, côtes, sorties longues en dénivelé, allure secondaire vs effort |
| `REPRISE_POST_COURSE` | 2-3 semaines EF uniquement, volume à 50 %, pas d'intensité |

---

## 3. Architecture cible

```
┌──────────────────────┐        HTTPS/JSON        ┌───────────────────────────────┐
│  SPA Vue 3 (Vite)    │ ───── JWT (athlète) ───▶ │  API Spring Boot 3.3 / Java 21│
│  servie par Nginx    │ ◀─────────────────────── │  :8080                        │
└──────────────────────┘                          └───────┬───────────────────────┘
                                                          │ JDBC
┌──────────────────────┐   HTTPS + clé de service         │
│  Skills Claude Code  │ ────────────────────────▶        │
│  (poste local)       │                                  ▼
└──────────────────────┘                        ┌────────────────────┐
                                                │  PostgreSQL 16     │
┌──────────────────────┐   HTTPS + clé service  │                    │
│  Worker Garmin       │ ────────────────────────▶└────────────────────┘
│  (Python, cron VPS)  │
└──────────────────────┘
```

**Trois clients, une seule API.** Aucun composant n'écrit en base directement hors du backend : la logique de dédup, de normalisation et de validation vit à un seul endroit.

### Stack

| Couche | Choix | Pourquoi |
|---|---|---|
| Back | **Java 21 + Spring Boot 3.3** (web, security, data-jpa, validation, actuator) | Choix de l'utilisateur ; records pour les DTOs, virtual threads pour l'I/O |
| DB | **PostgreSQL 16** | Relationnel, agrégats SQL, une seule base à sauvegarder |
| Migrations | **Liquibase** | Un changeset par table, ordre lexicographique |
| Front | **Vue 3 + TypeScript strict + Vite + Pinia + Vue Router + TailwindCSS** | Responsive mobile, types partagés avec les DTOs |
| Graphes | **Chart.js** (déjà vendorisé aujourd'hui) | Les 4 wrappers de `vue/js/charts.js` sont portés en composants |
| Worker Garmin | **Python 3.12 + `garminconnect`** | Il n'existe pas d'équivalent Java fiable ; le script actuel marche déjà |
| Reverse proxy | **Nginx + Let's Encrypt** | TLS, sert le build statique du front, proxy `/api` |
| Déploiement | **Docker Compose** sur le VPS | postgres + api + worker + nginx |

### Pourquoi le worker Garmin reste en Python

`scripts/garmin_sync.py` (472 lignes) encapsule déjà l'authentification OAuth Garmin, le cache de tokens par athlète, le garde-fou d'identité (`garminDisplayName`), la pagination et les 4 appels de détail par activité avec anti-429. La bibliothèque `garminconnect` n'a pas d'équivalent Java maintenu. Le réécrire serait une régression de fiabilité pour zéro gain. Il est **conservé et adapté** : au lieu d'écrire dans un CSV, il POSTe sur l'API d'ingestion.

---

## 4. Modèle de données

Conventions : `id UUID` (v7, ordonnable), `created_at` / `updated_at` en `TIMESTAMPTZ`, tout en UTC, `snake_case` en base et `camelCase` dans l'API.

### 4.1 Identité et athlète

```sql
CREATE TABLE athletes (
  id              UUID PRIMARY KEY,
  email           CITEXT UNIQUE NOT NULL,
  password_hash   TEXT NOT NULL,             -- BCrypt
  display_name    TEXT NOT NULL,
  timezone        TEXT NOT NULL DEFAULT 'Europe/Paris',
  role            TEXT NOT NULL DEFAULT 'ATHLETE',   -- ATHLETE | ADMIN
  garmin_display_name TEXT,                  -- garde-fou d'identité, cf. garmin_sync.py
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Le **profil sportif est sorti du cycle** — c'est le gros correctif de modélisation. Aujourd'hui les références, blessures, contraintes, séances signature et le renfo vivent dans `objectifs.json`, donc dans la prépa : ils sont perdus au cycle suivant. Ils appartiennent à l'athlète.

```sql
CREATE TABLE athlete_profiles (
  athlete_id      UUID PRIMARY KEY REFERENCES athletes(id) ON DELETE CASCADE,
  fc_max          SMALLINT,
  fc_repos        SMALLINT,
  vma_kmh         NUMERIC(4,1),
  volume_habituel_km  NUMERIC(5,1),
  jours_disponibles   TEXT[],                -- ['LUNDI','MERCREDI','SAMEDI','DIMANCHE']
  renfo_actif     BOOLEAN NOT NULL DEFAULT false,
  renfo_frequence SMALLINT,
  renfo_materiel  TEXT[],
  renfo_focus     TEXT,
  notes           TEXT,
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Records personnels, historisés : on garde la progression
CREATE TABLE personal_records (
  id          UUID PRIMARY KEY,
  athlete_id  UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  distance_m  INT NOT NULL,                  -- 10000, 21097, 42195…
  temps_sec   INT NOT NULL,
  date        DATE NOT NULL,
  contexte    TEXT,                          -- "Semi de Lyon 2026"
  source      TEXT NOT NULL DEFAULT 'DECLARE'  -- DECLARE | ACTIVITE
);
CREATE INDEX ON personal_records (athlete_id, distance_m, date DESC);

-- Blessures suivies, avec cycle de vie
CREATE TABLE injuries (
  id          UUID PRIMARY KEY,
  athlete_id  UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  zone        TEXT NOT NULL,                 -- "Tendon d'Achille droit"
  statut      TEXT NOT NULL,                 -- ACTIVE | SURVEILLANCE | RESOLUE
  palier      SMALLINT,                      -- 1|2|3 — protocole douleur COACH.md §4
  consignes   TEXT,
  debut       DATE NOT NULL,
  fin         DATE
);

-- Contraintes (vacances, chaleur, matériel, pro)
CREATE TABLE constraints (
  id          UUID PRIMARY KEY,
  athlete_id  UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  type        TEXT NOT NULL,                 -- VACANCES | METEO | MATERIEL | PRO | AUTRE
  debut       DATE, fin DATE,
  detail      TEXT NOT NULL
);

-- Séances signature (les "favorites" d'aujourd'hui)
CREATE TABLE signature_sessions (
  id          UUID PRIMARY KEY,
  athlete_id  UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  nom         TEXT NOT NULL,
  type_seance TEXT NOT NULL,                 -- enum SessionType
  description TEXT,
  distance_km NUMERIC(5,2),
  frequence_souhaitee TEXT,
  actif       BOOLEAN NOT NULL DEFAULT true  -- mise en pause si contre-indiquée (COACH.md §6)
);
```

### 4.2 Cycles

```sql
CREATE TABLE cycles (
  id              UUID PRIMARY KEY,
  athlete_id      UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  slug            TEXT NOT NULL,              -- "marathon-paris-2026", "libre-hiver-2026"
  nom             TEXT NOT NULL,
  type            TEXT NOT NULL,              -- PREPA | LIBRE
  statut          TEXT NOT NULL,              -- PLANIFIE | ACTIF | TERMINE | ABANDONNE
  date_debut      DATE NOT NULL,              -- toujours un lundi
  date_fin        DATE NOT NULL,              -- course (PREPA) ou fin d'horizon (LIBRE)

  -- PREPA uniquement
  course_nom      TEXT,
  course_date     DATE,
  course_distance_m INT,
  chrono_vise_sec INT,

  -- LIBRE uniquement
  ligne_directrice      TEXT,
  ligne_directrice_type TEXT,                 -- cf. §2.4
  horizon_semaines      SMALLINT,

  allures_cibles  JSONB NOT NULL DEFAULT '{}'::jsonb,   -- { "EF": {"secKm":400,"affichage":"6:40"}, … }
  bilan           TEXT,                       -- rempli à la clôture
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (athlete_id, slug),
  CONSTRAINT prepa_a_une_course CHECK (type <> 'PREPA' OR (course_date IS NOT NULL AND chrono_vise_sec IS NOT NULL)),
  CONSTRAINT libre_a_une_ligne  CHECK (type <> 'LIBRE' OR ligne_directrice IS NOT NULL)
);

-- Un seul cycle actif par athlète
CREATE UNIQUE INDEX un_seul_cycle_actif ON cycles (athlete_id) WHERE statut = 'ACTIF';
```

`allures_cibles` reste en **JSONB** : c'est un petit dictionnaire zone → `{secKm, affichage, note}`, lu et écrit atomiquement, dont les clés peuvent varier selon le type de cycle (un cycle trail parlera de `D+/h` plutôt que d'`AM`). C'est le seul document JSONB conservé du modèle actuel.

> **Note sur l'ambiguïté historique** : `alluresCibles` désigne aujourd'hui **un objet** dans `objectifs.json` et **une chaîne** dans une séance du plan — c'est le piège n°1 documenté dans `SCHEMA.md`. On le lève : `cycles.allures_cibles` (objet) vs `planned_sessions.allures_texte` (chaîne affichée).

### 4.3 Semaines et séances planifiées

Passage en **relationnel** (et non JSONB comme le suggérait le cadrage) : l'utilisateur veut pouvoir éditer une séance individuellement depuis l'app, et on doit joindre les séances aux activités réalisées. Un document JSONB rendrait ces deux opérations pénibles.

```sql
CREATE TABLE training_weeks (
  id              UUID PRIMARY KEY,
  cycle_id        UUID NOT NULL REFERENCES cycles(id) ON DELETE CASCADE,
  numero          SMALLINT NOT NULL,          -- 1..N dans le cycle
  date_debut      DATE NOT NULL,              -- lundi
  bloc            TEXT,                       -- BASE | DEVELOPPEMENT | SPECIFIQUE | AFFUTAGE | LIBRE | DECHARGE
  volume_cible_km NUMERIC(5,1) NOT NULL,
  nb_qualite_cible   SMALLINT,                -- surtout utile en mode LIBRE
  denivele_cible_m   INT,                     -- cycles trail
  detaillee       BOOLEAN NOT NULL DEFAULT true,  -- false = semaine de cibles seules (plan glissant)
  note            TEXT,
  UNIQUE (cycle_id, numero)
);
CREATE INDEX ON training_weeks (cycle_id, date_debut);

CREATE TABLE planned_sessions (
  id              UUID PRIMARY KEY,
  week_id         UUID NOT NULL REFERENCES training_weeks(id) ON DELETE CASCADE,
  cycle_id        UUID NOT NULL REFERENCES cycles(id) ON DELETE CASCADE,  -- dénormalisé, filtrage direct
  date            DATE NOT NULL,
  ordre           SMALLINT NOT NULL DEFAULT 0,   -- deux séances le même jour
  type            TEXT NOT NULL,              -- EF|SL|SEUIL|VMA|AM|COTES|RENFO|COURSE|CROSS|REPOS
  titre           TEXT NOT NULL,
  description     TEXT,
  statut          TEXT NOT NULL DEFAULT 'A_VENIR',  -- A_VENIR|VALIDEE|MANQUEE|DEPLACEE|ANNULEE
  allures_texte   TEXT,                       -- "Seuil 5:20 · EF 6:40" — affichage
  distance_cible_km NUMERIC(5,2),
  duree_cible_min   SMALLINT,
  focus           TEXT,                       -- obligatoire si type = RENFO
  commentaire_coach TEXT,                     -- écrit par le skill
  commentaire_athlete TEXT,                   -- écrit dans l'app
  signature_session_id UUID REFERENCES signature_sessions(id),
  activity_id     UUID REFERENCES activities(id) ON DELETE SET NULL,  -- rapprochement
  rapprochement   TEXT,                       -- AUTO | MANUEL | AUCUN
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT renfo_a_un_focus CHECK (type <> 'RENFO' OR focus IS NOT NULL)
);
CREATE INDEX ON planned_sessions (cycle_id, date);
CREATE INDEX ON planned_sessions (activity_id);
```

**Les 5 statuts** étendent les 3 actuels (`a_venir`/`validee`/`manquee`) avec `DEPLACEE` et `ANNULEE`, qui correspondent à des cas que `COACH.md` §4 traite déjà (« décaler d'un jour dans la même semaine », « sacrifier la séance ») mais que le modèle ne savait pas exprimer.

### 4.4 Activités réalisées

Rattachées à **l'athlète**, pas au cycle : l'historique est continu et traverse les cycles. Le cycle est retrouvé par la date.

```sql
CREATE TABLE activities (
  id              UUID PRIMARY KEY,
  athlete_id      UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  garmin_activity_id BIGINT,                  -- clé naturelle, dédup fiable
  source          TEXT NOT NULL,              -- GARMIN_API | CSV_IMPORT | MANUELLE
  type            TEXT NOT NULL,              -- RUN|TRAIL|TREADMILL|BIKE|SWIM|STRENGTH|HIKE|OTHER
  type_garmin     TEXT,                       -- typeKey brut, pour debug
  titre           TEXT,
  started_at      TIMESTAMPTZ NOT NULL,
  date_locale     DATE NOT NULL,              -- matérialisé pour les agrégats et la jointure au plan
  duree_sec       INT NOT NULL,
  duree_mouvement_sec INT,
  distance_m      INT,
  allure_moy_sec_km  INT,
  gap_moy_sec_km  INT,
  vitesse_max_ms  NUMERIC(5,2),
  fc_moy SMALLINT, fc_max SMALLINT, fc_min SMALLINT,
  cadence_moy SMALLINT, cadence_max SMALLINT,
  denivele_pos_m INT, denivele_neg_m INT,
  altitude_min_m INT, altitude_max_m INT,
  calories INT,
  te_aerobie NUMERIC(3,1), te_anaerobie NUMERIC(3,1), te_label TEXT,
  charge_entrainement NUMERIC(6,1),
  vo2max NUMERIC(4,1),
  longueur_foulee_m NUMERIC(4,2), oscillation_verticale NUMERIC(4,1), temps_contact_sol SMALLINT,
  puissance_moy INT, puissance_max INT,
  rpe SMALLINT, ressenti TEXT,                -- saisis dans l'app
  a_detail        BOOLEAN NOT NULL DEFAULT false,
  meteo           JSONB,                      -- {temperatureC, ressentiC, humidite, ventKmh, description}
  zones_fc        JSONB,                      -- [{zone, secondes, borneBasse}]
  imported_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (athlete_id, garmin_activity_id)
);
CREATE INDEX ON activities (athlete_id, started_at DESC);
CREATE INDEX ON activities (athlete_id, date_locale);
```

`meteo` et `zones_fc` restent en JSONB : petits, lus en bloc, jamais filtrés en SQL. Les **tours** sont en table, eux, parce qu'ils alimentent le graphe et le regroupement en blocs :

```sql
CREATE TABLE activity_laps (
  id              UUID PRIMARY KEY,
  activity_id     UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
  index_tour      SMALLINT NOT NULL,
  distance_m      INT,
  duree_sec       INT NOT NULL,
  allure_sec_km   INT,
  gap_sec_km      INT,
  fc_moy SMALLINT, fc_max SMALLINT,
  cadence_moy SMALLINT,
  puissance_moy INT,
  denivele_pos_m INT, denivele_neg_m INT,
  intensite       TEXT,                       -- WARMUP|ACTIVE|INTERVAL|REST|RECOVERY|COOLDOWN
  UNIQUE (activity_id, index_tour)
);
```

**Dédup** : `UNIQUE (athlete_id, garmin_activity_id)`. On ne dépend plus d'une égalité de `dateHeure` à la seconde. Pour les activités sans ID Garmin (import CSV historique, saisie manuelle), fallback sur une clé logique `sha256(athlete_id | started_at | duree_sec | distance_m)` stockée dans `garmin_activity_id` négatif ou une colonne `dedup_key` dédiée.

### 4.5 Journal et mémoire du coach

Le journal passe d'un markdown par semaine à des **entrées datées**, ce qui colle au suivi continu :

```sql
CREATE TABLE journal_entries (
  id          UUID PRIMARY KEY,
  athlete_id  UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  date        DATE NOT NULL,
  contenu     TEXT NOT NULL,
  humeur      SMALLINT,                       -- 1..5
  fatigue     SMALLINT,                       -- 1..5
  sommeil_h   NUMERIC(3,1),
  douleur     BOOLEAN NOT NULL DEFAULT false, -- déclencheur de question pour le coach
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON journal_entries (athlete_id, date DESC);
```

**La mémoire du coach** remplace le pavé `commentairesLibres`. C'est le point sensible : il faut que ça reste **court à relire**, sinon on recrée le problème.

```sql
CREATE TABLE coach_notes (
  id          UUID PRIMARY KEY,
  athlete_id  UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  cycle_id    UUID REFERENCES cycles(id) ON DELETE SET NULL,
  date        DATE NOT NULL,
  portee      TEXT NOT NULL,                  -- DURABLE | CYCLE | PONCTUELLE
  categorie   TEXT NOT NULL,                  -- DECISION | OBSERVATION | CONSIGNE | ALERTE
  titre       TEXT NOT NULL,                  -- ≤ 80 car., une ligne lisible
  contenu     TEXT NOT NULL,                  -- ≤ 500 car., contraint applicativement
  actif       BOOLEAN NOT NULL DEFAULT true,  -- une règle durable peut être levée
  remplace_id UUID REFERENCES coach_notes(id),-- chaînage quand une décision en annule une autre
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON coach_notes (athlete_id, portee, actif, date DESC);
```

**Règles anti-inflation** (à faire respecter par l'API et par les skills) :

1. **Trois portées, trois durées de vie.**
   - `DURABLE` — une règle permanente sur l'athlète (« pas de côtes tant que le tendon d'Achille est sensible », « renfo désactivé »). Peu nombreuses, toujours relues.
   - `CYCLE` — vraie pour le cycle courant seulement (« objectif recalé à 4h10 en semaine 9 »). Relue pendant le cycle, archivée à sa clôture.
   - `PONCTUELLE` — contexte d'une semaine. **Purgée au-delà de 8 semaines** par un job.
2. **`contenu` plafonné à 500 caractères**, validé côté API (`@Size(max=500)`). Une décision qui ne tient pas en 500 caractères est mal formulée.
3. **Superseding explicite** : quand une décision en annule une autre, le skill passe l'ancienne à `actif=false` et renseigne `remplace_id`. Pas d'accumulation contradictoire.
4. **Endpoint dédié au contexte coach** (`GET /coach-context`, §5.4) qui renvoie un paquet **borné** : toutes les notes `DURABLE actives` + les notes `CYCLE actives` du cycle courant + les 10 dernières `PONCTUELLE`. C'est ce que le skill lit — jamais la table entière.
5. Un **garde-fou de volumétrie** : si `DURABLE actives > 15` pour un athlète, l'API renvoie un `warning` dans la réponse — signal au skill qu'il faut consolider.

Enfin, les bilans hebdomadaires produits par `/prepa-update` :

```sql
CREATE TABLE weekly_reports (
  id            UUID PRIMARY KEY,
  athlete_id    UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  cycle_id      UUID NOT NULL REFERENCES cycles(id) ON DELETE CASCADE,
  week_id       UUID REFERENCES training_weeks(id) ON DELETE SET NULL,
  date_debut    DATE NOT NULL,
  bilan         TEXT NOT NULL,                -- les 4 points de COACH.md §8
  points_attention TEXT,
  changements   JSONB,                        -- [{seanceId, champ, avant, apres, raison}]
  consignes     TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (cycle_id, date_debut)
);
```

### 4.6 État d'analyse et clés de service

```sql
-- Remplace etat_analyse.json : quelles activités le coach a déjà passées en revue
CREATE TABLE analysis_state (
  activity_id   UUID PRIMARY KEY REFERENCES activities(id) ON DELETE CASCADE,
  athlete_id    UUID NOT NULL REFERENCES athletes(id) ON DELETE CASCADE,
  analysee_le   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Clés d'accès machine (skills Claude Code, worker Garmin)
CREATE TABLE service_keys (
  id          UUID PRIMARY KEY,
  nom         TEXT NOT NULL,                  -- "worker-garmin-vps", "claude-code-thibaut"
  key_hash    TEXT NOT NULL,                  -- SHA-256 de la clé, jamais la clé
  scopes      TEXT[] NOT NULL,                -- ['ingest','coach','read']
  athlete_id  UUID REFERENCES athletes(id),   -- NULL = clé multi-athlètes (worker)
  last_used_at TIMESTAMPTZ,
  revoked_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Identifiants Garmin par athlète, chiffrés au repos (AES-GCM, clé en variable d'env)
CREATE TABLE garmin_credentials (
  athlete_id    UUID PRIMARY KEY REFERENCES athletes(id) ON DELETE CASCADE,
  email_enc     BYTEA NOT NULL,
  password_enc  BYTEA NOT NULL,
  derniere_sync TIMESTAMPTZ,
  dernier_statut TEXT,                        -- OK | AUTH_ERROR | IDENTITE_KO | ERREUR
  dernier_message TEXT
);
```

---

## 5. API REST

Conventions reprises du cadrage : préfixe `/api/v1`, JSON `camelCase`, pagination cursor, enveloppe d'erreur unique `{ "error": { "code", "message", "details" } }`, `@ControllerAdvice` unique.

### 5.1 Authentification

Deux mécanismes coexistent dans la même `SecurityFilterChain`, dans cet ordre de filtres :

1. **`ServiceKeyAuthFilter`** — header `X-Service-Key`. Si présent et valide (hash trouvé, non révoqué, scope suffisant), authentifie un principal machine. Utilisé par les skills et le worker.
2. **`JwtAuthFilter`** — header `Authorization: Bearer`. Access token 15 min, refresh 30 j, HS256.

Chaque requête est **scopée par athlète** : un `AthleteGuard` vérifie que le principal peut accéder à l'`athleteId` visé (soi-même, ou clé de service autorisée). Aucun endpoint ne renvoie de données non filtrées.

| Méthode | Path | Rôle |
|---|---|---|
| POST | `/auth/login` | → `{accessToken, refreshToken}` |
| POST | `/auth/refresh` | |
| POST | `/auth/logout` | |
| GET | `/me` | athlète courant + profil + cycle actif |
| PATCH | `/me` | displayName, timezone |

Pas d'inscription publique : les comptes sont créés par un `AdminController` ou une commande de seed. Deux athlètes aujourd'hui, ça ne justifie pas un tunnel d'inscription.

### 5.2 Athlète et profil sportif

| Méthode | Path |
|---|---|
| GET / PUT | `/athletes/{id}/profile` |
| GET / POST / DELETE | `/athletes/{id}/records` |
| GET / POST / PATCH | `/athletes/{id}/injuries` |
| GET / POST / PATCH / DELETE | `/athletes/{id}/constraints` |
| GET / POST / PATCH / DELETE | `/athletes/{id}/signature-sessions` |

### 5.3 Cycles et plan

| Méthode | Path | Rôle |
|---|---|---|
| GET | `/athletes/{id}/cycles` | liste, avec le cycle actif en tête |
| POST | `/athletes/{id}/cycles` | crée un cycle (PREPA ou LIBRE) |
| GET | `/cycles/{id}` | cycle + semaines + séances + agrégats |
| PATCH | `/cycles/{id}` | nom, allures cibles, ligne directrice, horizon |
| POST | `/cycles/{id}/close` | clôture : bilan, statut `TERMINE` |
| POST | `/cycles/{id}/convert` | `LIBRE` → `PREPA` (course + date + chrono) et inversement |
| GET | `/cycles/{id}/weeks` | |
| PUT | `/cycles/{id}/plan` | **remplacement transactionnel** du plan (skills) |
| PATCH | `/weeks/{id}` | cibles, bloc, note |
| POST | `/weeks/{id}/sessions` | ajoute une séance |
| PATCH | `/sessions/{id}` | **portée variable selon le principal — cf. §5.6** |
| DELETE | `/sessions/{id}` | skills uniquement |
| POST | `/sessions/{id}/link-activity` | rapprochement manuel |

`PUT /cycles/{id}/plan` prend le plan complet (semaines + séances) et le remplace en une transaction, **en préservant** les `activity_id`, `statut` et `commentaire_athlete` des séances passées — la régénération d'un plan glissant ne doit jamais effacer l'historique.

### 5.4 Endpoints dédiés au coach (skills)

Ce sont les plus importants : ils remplacent `analyze.py` et garantissent que le skill reçoit un contexte **borné et pré-mâché**, au lieu de lire des fichiers entiers.

| Méthode | Path | Renvoie |
|---|---|---|
| GET | `/athletes/{id}/coach-context` | **Le paquet de départ de `/prepa-update`** : athlète + profil + blessures actives + contraintes en cours + cycle actif + semaine courante + notes coach bornées (§4.5) + 3 dernières entrées de journal. Cible : < 4 000 tokens. |
| GET | `/athletes/{id}/analysis?jours=90` | Équivalent de `analyze.py` global : volume hebdo (ISO week), allure EF réelle, meilleurs efforts, tendances FC, comparaison avant/pendant le cycle. **Calculé en SQL**, pas en Python. |
| GET | `/athletes/{id}/activities/new` | Activités non encore analysées (`analysis_state`), avec leur fiche détaillée (tours groupés en blocs, zones FC, météo). Équivalent de `analyze.py --nouvelles`. |
| POST | `/athletes/{id}/activities/mark-analyzed` | `{ activityIds: [...] }` — clôture de `/prepa-update`. |
| GET | `/athletes/{id}/reconciliation?week=...` | Rapprochement pré-calculé plan ↔ activités de la semaine, avec les **écarts déjà qualifiés** : `ECART_DISTANCE_20PCT`, `SEANCE_CLE_MANQUEE`, `FC_SUSPECTE`, `VOLUME_SOUS_CIBLE`. Ce sont exactement les déclencheurs de `COACH.md` §5 — le back les détecte, le skill décide quoi en faire. |
| POST | `/athletes/{id}/coach-notes` | Écrit une note (validation des 500 caractères, du superseding) |
| PATCH | `/coach-notes/{id}` | Désactive / remplace une note |
| POST | `/cycles/{id}/reports` | Enregistre le bilan hebdo |

**Le point clé** : `reconciliation` fait côté back le travail que le skill fait aujourd'hui à la main en lisant 4 fichiers. Le skill reçoit une liste d'écarts qualifiés et se concentre sur ce qu'il sait faire — juger et décider.

### 5.5 Ingestion Garmin

| Méthode | Path | Rôle |
|---|---|---|
| POST | `/ingest/activities` | Batch d'activités normalisées (worker). Réponse : `{recues, importees, doublons, erreurs[]}`. **Idempotent.** |
| POST | `/ingest/activities/{garminId}/details` | Tours + zones FC + météo d'une activité |
| GET | `/ingest/athletes` | Liste des athlètes à synchroniser + `derniere_sync` (le worker s'en sert pour calculer sa fenêtre) |
| POST | `/ingest/sync-status` | Le worker remonte le résultat de son passage |
| POST | `/athletes/{id}/sync` | Déclenche un sync à la demande (app ou skill) — pose un flag que le worker lit |

### 5.6 Édition depuis l'app : ce qui est permis, ce qui ne l'est pas

L'utilisateur veut pouvoir corriger des choses sans repasser par Claude, **sans pour autant casser la cohérence du plan**. La règle : *un athlète peut décrire la réalité, il ne peut pas redessiner l'entraînement.*

| Action | Athlète (app) | Skill (clé de service) |
|---|---|---|
| Marquer une séance `VALIDEE` / `MANQUEE` | ✅ | ✅ |
| Commenter une séance (`commentaire_athlete`) | ✅ | ❌ |
| Écrire / modifier le journal | ✅ | ❌ |
| Saisir RPE, ressenti sur une activité | ✅ | ✅ |
| Rapprocher manuellement une activité d'une séance | ✅ | ✅ |
| Déplacer une séance **dans la même semaine** | ✅ (passe en `DEPLACEE`) | ✅ |
| Signaler une blessure / une contrainte | ✅ | ✅ |
| Ajouter une activité manuelle (séance hors Garmin) | ✅ | ✅ |
| Changer le **type** ou les **allures** d'une séance | ❌ | ✅ |
| Changer la **distance / durée cible** | ❌ | ✅ |
| Ajouter ou supprimer une séance | ❌ | ✅ |
| Modifier les **cibles hebdo** ou le bloc | ❌ | ✅ |
| Modifier les **allures cibles du cycle** ou le chrono visé | ❌ | ✅ |
| Créer, convertir ou clôturer un cycle | ❌ | ✅ |
| Écrire une `coach_note` | ❌ | ✅ |

Implémentation : un `@PreAuthorize` par champ serait illisible. À la place, `PATCH /sessions/{id}` accepte **deux DTOs distincts** (`AthleteSessionPatch` et `CoachSessionPatch`) sélectionnés selon le type de principal ; tout champ hors périmètre renvoie `403 FORBIDDEN_FIELD` en nommant le champ. Le front, lui, n'affiche simplement pas les contrôles interdits — et affiche à la place « Demande à ton coach » avec la liste des modifications à demander.

Quand l'athlète déplace une séance, une `coach_note` `PONCTUELLE` est créée automatiquement (« séance du 12/03 déplacée au 13/03 par l'athlète ») — le prochain `/prepa-update` la voit.

---

## 6. Backend Spring Boot

### Arborescence

```
prepa-api/
├── build.gradle.kts
├── src/main/java/app/prepa/
│   ├── PrepaApplication.java
│   ├── athlete/        Athlete, AthleteProfile, records, injuries, constraints, signature
│   ├── cycle/          Cycle, TrainingWeek, PlannedSession + services
│   ├── activity/       Activity, ActivityLap, normalisation, dédup
│   ├── journal/        JournalEntry
│   ├── coach/          CoachContextService, ReconciliationService, CoachNote, WeeklyReport
│   ├── analysis/       AnalysisService — les agrégats SQL (ex-analyze.py)
│   ├── ingest/         IngestController, CsvImportService (migration)
│   ├── auth/           JwtAuthFilter, ServiceKeyAuthFilter, AthleteGuard
│   └── infra/          SecurityConfig, ExceptionHandler, Jackson, crypto AES-GCM
└── src/main/resources/
    ├── application.yml, application-local.yml, application-prod.yml
    └── db/changelog/…
```

### Les services à forte valeur

**`AnalysisService`** — porte `analyze.py` en SQL. Notamment :

- Volume hebdomadaire par semaine ISO : `date_trunc('week', date_locale)`, avec `generate_series` pour ne pas trouer les semaines sans sortie.
- **Allure EF réelle** — l'heuristique actuelle (`distance ≥ 5 km` ET `fc_moy ≤ 145` ET `D+ < 120 m`) est portée, mais le seuil FC devient **relatif** : `fc_moy ≤ 0,75 × athlete_profiles.fc_max` quand la FC max est connue, avec repli sur 145. Le seuil en dur est faux pour un athlète à 165 de FC max.
- **Meilleurs efforts** — aujourd'hui `bestEffort()` est une **approximation** (`distance × allure moyenne de la sortie entière`), d'où le sous-titre « estimés ». Avec les tours en base, on calcule une **vraie fenêtre glissante** sur `activity_laps` : meilleure allure sur 1, 5, 10, 21,1 km. C'est un gain réel de justesse.
- Comparaison avant/pendant cycle, assiduité hors renfo, volume réalisé vs cible.

**`ReconciliationService`** — porte `joinActivitiesToPlan()` : jointure par date, et si plusieurs activités le même jour, appariement par proximité de `distance_cible_km` (avec un `Set` de séances déjà consommées, comme aujourd'hui). Puis qualification des écarts selon `COACH.md` §5.

**`LapBlockService`** — porte `grouperEnBlocs()` : regroupement des tours consécutifs de même `intensite`, allure du bloc = `Σdurée / Σdistance`, **FC moyenne pondérée par la durée** (pas la moyenne des moyennes), `fcDebut`/`fcFin` pour la dérive. Et `estStructuree()` = plus d'une intensité distincte.

**`GarminNormalizer`** — porte la table `COLONNES` de `build_data.py` et les convertisseurs. Sert à l'import CSV de migration ; le worker envoie déjà du normalisé.

### Correctifs de bugs connus à ne pas reconduire

| Bug actuel | Correctif |
|---|---|
| `analyze.py` filtre `"Course sur tapis"` alors que `garmin_sync.py` écrit `"Course sur tapis roulant"` → les séances tapis disparaissent des synthèses | Enum `ActivityType` unique, mapping centralisé, table de correspondance testée |
| Jointure activité ↔ détail sur `dateHeure` à la seconde | `garmin_activity_id` comme clé naturelle |
| `bestEffort()` approximé | Fenêtre glissante sur `activity_laps` |
| Seuil FC EF en dur à 145 | Relatif à `fc_max` |
| `alluresCibles` objet vs chaîne | Deux champs distincts |

### Tests

- Unitaires (JUnit 5 + AssertJ) sur `AnalysisService`, `ReconciliationService`, `LapBlockService`, `GarminNormalizer`. Ces quatre-là portent toute la logique métier : viser une couverture élevée.
- Intégration avec **Testcontainers Postgres** + MockMvc sur les endpoints, en particulier `/coach-context` (bornage), `/ingest/activities` (idempotence : rejouer deux fois le même batch ⇒ 0 doublon) et `PATCH /sessions/{id}` (matrice de permissions §5.6).
- Fixtures : réutiliser les vrais `garmin.csv` et `garmin_raw/*.json` du projet actuel comme jeux de test.

---

## 7. Front Vue 3

### Arborescence

```
prepa-web/
├── src/
│   ├── api/          http.ts (axios + JWT + refresh), athletes.ts, cycles.ts, activities.ts, coach.ts
│   ├── stores/       auth.ts, athlete.ts, cycle.ts, activities.ts, ui.ts
│   ├── views/        LoginView, DashboardView, PlanView, ActivitiesView, StatsView,
│   │                 JournalView, ProfileView, CyclesView
│   ├── components/
│   │   ├── cycle/    WeekBlock, SessionCard, SessionStatusToggle, CycleBanner, CycleSwitcher
│   │   ├── activity/ ActivityList, ActivityDetail, LapTable, LapChart, HrZoneBar, WeatherChips
│   │   ├── charts/   VolumeChart, PaceChart, HrTrendChart  (portage de vue/js/charts.js)
│   │   └── ui/       boutons, modales, toasts, empty states, skeletons
│   ├── composables/  useCycle, useFormat (allure m:ss, durée, distance), useKeyboardNav
│   └── types/        générés depuis les DTOs (openapi-typescript sur /v3/api-docs)
```

Les types TS sont **générés depuis l'OpenAPI** du back (springdoc) : pas de drift entre front et back.

### Écrans

**Dashboard** — l'écran d'accueil, adaptatif selon le type de cycle :
- Bandeau : en `PREPA`, « Marathon de Paris — J-64 » + barre de progression ; en `LIBRE`, la ligne directrice + « semaine 4/12 ».
- Séance du jour en grande carte, avec les boutons `Fait` / `Manqué` (permis à l'athlète).
- La semaine en cours, 7 mini-cartes.
- KPI : volume réalisé vs cible, assiduité hors renfo, charge des 4 dernières semaines.
- Dernier bilan de coach (extrait cliquable).

**Plan** — semaines empilées, celle en cours auto-scrollée en tête. En mode libre, les semaines non détaillées s'affichent en « cibles seulement », visuellement distinctes, avec la mention « ton coach détaillera cette semaine au prochain point ». C'est important : l'absence de séances doit se lire comme **volontaire**, pas comme un trou.

**Séances** (activités réalisées) — reprend l'existant, qui est bon : liste chronologique + fiche détaillée, graphe d'allure par tour, barre de zones FC (zones < 30 s filtrées, libellé masqué sous 5 %), chips météo et training effect, navigation ◀ ▶ + flèches clavier + swipe. La bascule **Blocs / Tours** n'apparaît que sur les séances structurées.

**Stats** — volume hebdo, allures par type de séance (moyenne affichée seulement pour les types continus EF/SL/AM, pas pour VMA/Seuil/Côtes), tendances FC, **comparaison entre cycles** (nouveau : « ce cycle libre vs la prépa marathon »), records par distance.

**Journal** — entrées datées, saisie rapide avec humeur / fatigue / douleur.

**Profil** — profil sportif, records, blessures, contraintes, séances signature, renfo. Éditable.

**Cycles** — timeline des cycles successifs, et les actions de transition (clôturer, démarrer un libre, passer en prépa). C'est l'écran qui matérialise le changement fonctionnel.

### Principes UI

- Mobile responsive, en ligne uniquement (pas de PWA ni de service worker pour l'instant).
- Thème clair/sombre par variables CSS (le `style.css` actuel en a déjà un bon jeu — le portage vers les tokens Tailwind est direct).
- **Aucun champ technique exposé** : allures en `m:ss/km`, jamais en secondes ; pas d'UUID, pas de JSON.
- Les actions interdites à l'athlète ne sont **pas affichées grisées** — elles sont remplacées par un lien « Demander à mon coach ».

---

## 8. Worker Garmin

`scripts/garmin_sync.py` est repris comme base et adapté :

```
worker/
├── sync.py              boucle principale
├── garmin_client.py     auth, cache tokens (~/.garminconnect/<athleteId>), garde-fou d'identité
├── normalize.py         API Garmin → DTO d'ingestion (porte details.py)
├── api_client.py        POST vers /api/v1/ingest/*, retry, backoff
└── Dockerfile
```

Déroulé d'un passage :

1. `GET /ingest/athletes` → liste des athlètes + `derniere_sync` + credentials (déchiffrées par l'API, servies sur clé de service à scope `ingest`).
2. Pour chaque athlète : authentification Garmin, **vérification d'identité** (`display_name` vs `garmin_display_name` — ce garde-fou existe déjà et évite d'écrire les séances d'un athlète chez un autre : le conserver absolument), récupération des activités depuis `derniere_sync`.
3. Normalisation → `POST /ingest/activities` par batch.
4. Pour chaque activité neuve : 4 appels de détail (splits, zones FC, météo, activité complète) avec la pause anti-429 de 0,4 s, puis `POST /ingest/activities/{id}/details`.
5. `POST /ingest/sync-status` avec le résultat.

Planification : cron à 04h00, plus un déclenchement à la demande (l'API pose un flag, le worker le lit toutes les 5 minutes). En cas d'échec sur un athlète, on passe au suivant et on remonte l'erreur — jamais de plantage global.

Les secrets Garmin sont **chiffrés en base** (AES-GCM, clé dans l'environnement du conteneur API) et ne transitent que vers le worker, sur clé de service dédiée.

---

## 9. Les skills Claude Code

Les trois skills sont réécrits pour parler à l'API. Ils gardent leur logique de coach — c'est le transport qui change.

### Configuration

Un fichier `~/.prepa-cli/config.json` : `{ apiUrl, serviceKey, athleteParDefaut }`. Un petit CLI Python `prepa` (ou de simples `curl` dans les skills) encapsule les appels.

### `/prepa-sync` — inchangé dans l'esprit, trivial désormais

Le sync ne se fait plus en local : il tourne sur le VPS. Le skill devient :
1. `POST /athletes/{id}/sync` (ou pour tous les athlètes).
2. Attente courte, puis `GET /ingest/sync-status`.
3. Rapport factuel des activités récupérées.

Il ne fait toujours **aucune analyse** et **aucune adaptation** — les activités restent « neuves » pour le prochain update. Plus de `build_data.py`, plus de commit git : la donnée est en base.

### `/prepa-update` — le skill métier

1. Question ouverte en texte libre sur la semaine écoulée (pas d'AskUserQuestion — c'est la grille de lecture).
2. `GET /athletes/{id}/coach-context` — le paquet borné (§5.4).
3. `GET /athletes/{id}/reconciliation?week=...` — les écarts **déjà qualifiés**.
4. `GET /athletes/{id}/activities/new` — l'exécution détaillée des séances neuves (tours groupés en blocs). Rappel de `COACH.md` : on juge une séance à blocs **sur ses tours**, jamais sur sa moyenne.
5. Pour chaque écart qualifié, `AskUserQuestion` **avant** toute adaptation (déclencheurs `COACH.md` §5 : écart > 20 %, séance clé sautée, douleur, FC suspecte, volume sous la cible).
6. `PATCH /sessions/{id}` pour les statuts et commentaires de coach ; `PUT /cycles/{id}/plan` si la restructuration est large.
7. En mode `LIBRE` : **détailler une semaine supplémentaire** du plan glissant et réajuster les cibles restantes.
8. `POST /athletes/{id}/coach-notes` pour les décisions — en respectant les 500 caractères et en désactivant les notes remplacées.
9. `POST /cycles/{id}/reports` avec le bilan en 4 points (`COACH.md` §8).
10. `POST /athletes/{id}/activities/mark-analyzed`.

### `/prepa-cycle` — remplace `/prepa-init`

Un seul skill pour toutes les transitions, qui demande d'abord ce qu'on veut faire :

- **Nouveau cycle prépa** → questionnaire actuel (course, date, chrono, records, dispos, blessures, contraintes, séances signature, renfo) + vérification de réalisme (`COACH.md` §3 : marathon ≈ semi × 2 + 8-12 min ; AM plus rapide que le RP 10 km ⇒ challenger) + génération du plan complet → `POST /cycles` puis `PUT /cycles/{id}/plan`.
- **Nouveau cycle libre** → ligne directrice (texte libre + type), horizon en semaines, jours disponibles, volume de départ → génération des 4 premières semaines détaillées + cibles jusqu'à l'horizon.
- **Passer en prépa** (depuis un libre) → `POST /cycles/{id}/convert`, vérification du temps restant, régénération du plan.
- **Clôturer un cycle** → bilan de cycle depuis `GET /analysis`, `POST /cycles/{id}/close`, proposition d'enchaîner sur un cycle de reprise.

Le questionnaire ne redemande plus le profil sportif si l'athlète existe : il est en base et persiste entre les cycles.

### Mise à jour de `COACH.md`

Le document est recopié tel quel, avec **une section nouvelle** sur le cycle libre : comment dériver des allures sans chrono visé (depuis les records récents, `COACH.md` §3 fonctionne déjà à l'envers), quelles trames par type de ligne directrice (§2.4), et comment gérer un plan glissant (détailler S+1 à S+4, cibles au-delà, ne jamais empiler).

---

## 10. Migration des données

Toute la donnée existante est migrée. Script Python one-shot `migration/import_legacy.py`, lancé contre l'API en local avec une clé de service :

1. **Athlètes** — un par `profiles/<id>/profile.json`, avec `garminDisplayName`. Mots de passe définis manuellement.
2. **Profil sportif** — extrait d'`objectifs.json` : `references` → `personal_records` (avec les dates quand elles sont déductibles), `blessures` → `injuries`, `contraintes` → `constraints`, `seancesFavorites` → `signature_sessions`, `renforcement` → `athlete_profiles`.
3. **Cycles** — une prépa `marathon-2026-10` par athlète, type `PREPA`, statut `ACTIF`, allures cibles reprises telles quelles.
4. **Plan** — `plan.json.semaines[]` → `training_weeks` + `planned_sessions`. Mapping des statuts : `a_venir`→`A_VENIR`, `validee`→`VALIDEE`, `manquee`→`MANQUEE`. Les `commentaireCoach` sont conservés.
5. **Activités** — `garmin.csv` (254 lignes chez thibaut, 142 chez camille) parsé par `POST /ingest/activities/csv`, plus les `garmin_raw/*.json` (76 fichiers au total) pour les tours, zones FC et météo. **C'est là qu'on récupère les `activityId` Garmin** qui manquent au CSV : les 76 séances détaillées sont matchées par `dateHeure`, le reste reçoit une clé de dédup logique.
6. **Journal** — `journal.md` découpé par `## Semaine N` ; chaque entrée est datée au lundi de la semaine correspondante du plan.
7. **Mémoire coach** — `objectifs.commentairesLibres` (~6 000 car. chez thibaut) est **relu et découpé manuellement** en `coach_notes` typées. C'est un travail éditorial, à faire avec Claude en assistant : on en attend une quinzaine de notes, dont 3-5 `DURABLE`. C'est l'occasion de repartir sur une mémoire propre plutôt que d'importer le pavé.
8. **`etat_analyse.json`** → `analysis_state`.

**Vérification de migration** : un script compare, pour chaque athlète, le nombre d'activités, le volume total en km, le nombre de séances par statut et le volume hebdomadaire, entre l'ancien `activites.json` et la base. Écart toléré : zéro.

Le dépôt actuel est **archivé en lecture seule** après migration réussie — il reste la référence pour `COACH.md` et les fixtures de test.

---

## 11. Déploiement VPS

`docker-compose.yml` avec quatre services :

| Service | Note |
|---|---|
| `postgres` | volume persistant, `pg_dump` quotidien vers un stockage externe, rétention 30 j |
| `api` | image du back, variables d'env : `DB_URL`, `JWT_SECRET`, `CRYPTO_KEY`, `SPRING_PROFILES_ACTIVE=prod` |
| `worker` | image Python, cron interne à 04h00, volume pour le cache de tokens Garmin |
| `nginx` | TLS Let's Encrypt (certbot), sert le build statique du front, proxy `/api` → `api:8080` |

- Aucun port Postgres exposé publiquement — seul le réseau Docker interne y accède.
- Secrets dans un `.env` hors dépôt (un `.env.example` documenté est versionné).
- CI GitHub Actions : lint + tests + build des images + push registry ; déploiement par `docker compose pull && up -d` sur le VPS.
- `/actuator/health` derrière une allowlist d'IP, monitoré.
- **Tester la restauration du backup au moins une fois** avant de supprimer le dépôt de fichiers. Un backup jamais restauré n'est pas un backup.

---

## 12. Ordre d'implémentation

| Jalon | Contenu | Fin attendue |
|---|---|---|
| **M1 — Socle** | Projet Spring + Liquibase (toutes les tables) + Docker Compose local + auth JWT et clé de service + `/me` | On peut se connecter et lire un athlète vide |
| **M2 — Données** | Entités, repositories, CRUD athlète/profil/cycles/semaines/séances/activités. `GarminNormalizer` + import CSV. **Migration du legacy jouée en local.** | La base locale contient les vraies données des deux athlètes, vérifiées |
| **M3 — Analyse** | `AnalysisService`, `LapBlockService`, `ReconciliationService`, endpoints `/analysis`, `/coach-context`, `/reconciliation`, `/activities/new` | Les chiffres produits par l'API **égalent** ceux de `analyze.py` sur les mêmes données — c'est le test de non-régression clé |
| **M4 — Front** | SPA Vue : login, dashboard, plan, séances, stats, journal, profil, cycles. Portage des graphes | L'app remplace `vue/index.html`, à iso-fonctionnalité |
| **M5 — Cycles libres** | Plan glissant, transitions de cycle, section libre de `COACH.md`, écran Cycles | On peut clôturer la prépa marathon et enchaîner sur un cycle libre |
| **M6 — Skills** | Réécriture des trois skills contre l'API, `/prepa-cycle` | Un `/prepa-update` complet tourne de bout en bout |
| **M7 — Prod** | Worker Garmin conteneurisé + cron, déploiement VPS, TLS, backups, migration de production | Le sync tourne seul chaque nuit, l'app est accessible depuis le téléphone |

M1 à M3 sont la fondation et ne doivent pas être bâclés : si `/coach-context` et `/reconciliation` sont bons, les skills deviennent simples.

---

## 13. Vérification

**À chaque jalon**

- `./gradlew test` — unitaires + intégration Testcontainers.
- `npm run test:unit` côté front.

**Test de non-régression de l'analyse (M3, le plus important)**

Sur les données migrées des deux athlètes, comparer point par point la sortie de `python3 scripts/analyze.py --profil thibaut --prepa marathon-2026-10` et celle de `GET /api/v1/athletes/{id}/analysis` : volume hebdo semaine par semaine, nombre de séances, allure EF moyenne, plus longues sorties, tendances FC. Tout écart doit être **expliqué** (les corrections voulues du §6 en produiront : meilleurs efforts en fenêtre glissante, seuil FC relatif, séances tapis désormais comptées) ou **corrigé**.

**Idempotence de l'ingestion**

Rejouer deux fois le même batch sur `/ingest/activities` ⇒ `{importees: 0, doublons: N}` au second passage, et aucune ligne dupliquée en base.

**Matrice de permissions (§5.6)**

Un test d'intégration par ligne du tableau : le principal athlète reçoit `403 FORBIDDEN_FIELD` sur chaque champ réservé au coach, et `200` sur chacun des siens.

**Parcours de bout en bout**

1. Se connecter sur mobile, vérifier le dashboard de la prépa en cours.
2. Lancer un sync à la demande, voir apparaître une activité de la veille avec ses tours et sa météo.
3. Marquer une séance comme faite depuis l'app, vérifier que l'assiduité bouge.
4. Lancer `/prepa-update`, vérifier qu'il questionne bien un écart réel et que la modification du plan apparaît dans l'app après rechargement.
5. Clôturer la prépa marathon, créer un cycle libre « maintenir la charge » sur 12 semaines, vérifier que 4 semaines sont détaillées et les 8 suivantes en cibles.
6. Relancer `/prepa-update` une semaine plus tard, vérifier qu'une cinquième semaine est détaillée.

**Bornage du contexte coach**

Mesurer la taille de `GET /coach-context` : si la réponse dépasse ~4 000 tokens, la règle de bornage des `coach_notes` (§4.5) ne tient pas et doit être resserrée. C'est le garde-fou qui empêche de recréer le problème du pavé `commentairesLibres`.
