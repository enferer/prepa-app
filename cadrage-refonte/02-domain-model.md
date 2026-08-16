# 02 — Modèle de domaine & base de données

## Principes

- **PostgreSQL** relationnel + colonnes **JSONB** pour les documents mouvants (`objectifs`, `plan`, `report`).
- Toutes les tables portent `id UUID`, `created_at`, `updated_at`. `id` en `uuid v7` (ordonnable par temps — utile pour la pagination cursor).
- **Soft delete** (`deleted_at`) sur `users` et `prepas` uniquement — RGPD nous impose la suppression dure sur demande, gérée par un job séparé.
- Timezone : tout en `TIMESTAMPTZ` UTC. La timezone utilisateur est stockée sur `users.timezone`, appliquée uniquement au rendu.

## Diagramme entités

```
users (1) ──< prepas (1) ──< activities
                │
                ├──< journal_entries
                │
                └──< coach_reports         (générés par /weekly-update)

users (1) ──< chat_sessions ──< chat_messages
users (1) ──< usage_ledger                 (append-only, quotas LLM)
users (1) ──── subscription (1..1)
users (1) ──< garmin_credentials  (V2)
```

## Tables

### `users`

```sql
CREATE TABLE users (
  id            UUID PRIMARY KEY,
  email         CITEXT UNIQUE NOT NULL,
  password_hash TEXT,                       -- null si OAuth-only
  display_name  TEXT NOT NULL,
  timezone      TEXT NOT NULL DEFAULT 'Europe/Paris',
  locale        TEXT NOT NULL DEFAULT 'fr-FR',
  role          TEXT NOT NULL DEFAULT 'USER',   -- USER | ADMIN
  email_verified_at TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ
);
CREATE INDEX ON users (lower(email)) WHERE deleted_at IS NULL;
```

### `subscriptions`

```sql
CREATE TABLE subscriptions (
  user_id       UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  plan          TEXT NOT NULL,             -- FREE | PRO
  status        TEXT NOT NULL,             -- ACTIVE | PAST_DUE | CANCELLED
  stripe_customer_id TEXT,                 -- V2
  stripe_subscription_id TEXT,
  current_period_end TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

À la création d'un user, insert par trigger `plan='FREE'`, `status='ACTIVE'`.

### `prepas`

```sql
CREATE TABLE prepas (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  slug          TEXT NOT NULL,             -- "marathon-paris-2026"
  is_active     BOOLEAN NOT NULL DEFAULT true,
  objectifs     JSONB NOT NULL,            -- schéma cf. coach/SCHEMA.md
  plan          JSONB NOT NULL,            -- schéma cf. coach/SCHEMA.md
  plan_version  INT NOT NULL DEFAULT 1,    -- incrémenté à chaque adaptation
  race_date     DATE NOT NULL,             -- extrait de objectifs.course.date, matérialisé pour indexer
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ,
  UNIQUE (user_id, slug)
);
CREATE INDEX ON prepas (user_id) WHERE deleted_at IS NULL;
CREATE INDEX ON prepas USING gin (plan jsonb_path_ops);
```

`objectifs` et `plan` respectent **exactement** le contrat de [`coach/SCHEMA.md`](../coach/SCHEMA.md). On ne les éclate pas — c'est intentionnel : ils sont produits/lus atomiquement par le LLM et par le front.

**`plan_version`** est le hash logique. Chaque `POST /prepas/{id}/weekly-update` incrémente cette version, ce qui permet au front de savoir si son cache local est périmé.

### `activities`

Ce sont les séances **réellement effectuées** (importées Garmin CSV puis API). Elles sont **normalisées** — pas de JSON brut.

```sql
CREATE TABLE activities (
  id            UUID PRIMARY KEY,
  prepa_id      UUID NOT NULL REFERENCES prepas(id) ON DELETE CASCADE,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,  -- dénormalisé pour filtrage rapide
  source        TEXT NOT NULL,             -- GARMIN_CSV | GARMIN_API | MANUAL
  external_id   TEXT,                      -- ID Garmin si dispo (dedup)
  activity_type TEXT NOT NULL,             -- RUN | TRAIL | TREADMILL | BIKE | SWIM | STRENGTH | OTHER
  started_at    TIMESTAMPTZ NOT NULL,
  duration_sec  INT NOT NULL,
  distance_m    INT,                       -- null pour renfo
  avg_pace_sec_km  INT,
  avg_hr        SMALLINT,
  max_hr        SMALLINT,
  elevation_gain_m INT,
  calories      INT,
  title         TEXT,
  notes         TEXT,
  raw           JSONB,                     -- payload d'origine (CSV row / Garmin JSON) pour debug
  imported_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (prepa_id, source, external_id)   -- clé de dédup, external_id NULL toléré multi
);
CREATE INDEX ON activities (prepa_id, started_at DESC);
CREATE INDEX ON activities (user_id, started_at DESC);
```

Les agrégats (volume hebdo, allures moyennes) sont **calculés à la volée** en SQL — pas de table `weekly_stats` prématurée. On matérialisera si besoin quand la charge le justifie.

### `journal_entries`

```sql
CREATE TABLE journal_entries (
  id            UUID PRIMARY KEY,
  prepa_id      UUID NOT NULL REFERENCES prepas(id) ON DELETE CASCADE,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  week_number   INT NOT NULL,              -- correspond à plan.semaines[].numero
  content       TEXT NOT NULL,
  mood          SMALLINT,                  -- 1..5, optionnel
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (prepa_id, week_number)
);
```

### `coach_reports`

Sortie structurée du LLM après un `/weekly-update`. Historisé — permet de montrer à l'utilisateur son historique et de recharger contexte compact.

```sql
CREATE TABLE coach_reports (
  id            UUID PRIMARY KEY,
  prepa_id      UUID NOT NULL REFERENCES prepas(id) ON DELETE CASCADE,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  week_number   INT NOT NULL,
  summary       TEXT NOT NULL,             -- ce que l'utilisateur voit
  changes       JSONB NOT NULL,            -- liste d'adaptations appliquées au plan
  plan_version_before INT NOT NULL,
  plan_version_after  INT NOT NULL,
  llm_call_id   UUID REFERENCES llm_calls(id),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON coach_reports (prepa_id, week_number DESC);
```

### `chat_sessions` / `chat_messages`

Le chat coach est **session-scopé** — le back garde un fil court (rolling window ~10 tours) pour permettre le follow-up ("et sinon, tu me conseilles quoi ?") sans réenvoyer tout le contexte à chaque tour.

```sql
CREATE TABLE chat_sessions (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  prepa_id      UUID NOT NULL REFERENCES prepas(id) ON DELETE CASCADE,
  title         TEXT,                      -- généré au premier msg
  last_message_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON chat_sessions (user_id, last_message_at DESC);

CREATE TABLE chat_messages (
  id            UUID PRIMARY KEY,
  session_id    UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
  role          TEXT NOT NULL,             -- USER | ASSISTANT | SYSTEM (jamais exposé)
  content       TEXT NOT NULL,
  tokens_in     INT,
  tokens_out    INT,
  llm_call_id   UUID REFERENCES llm_calls(id),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON chat_messages (session_id, created_at);
```

### `llm_calls` — journal complet des appels LLM

Une ligne par appel LLM. Sert à : facturation interne, debug, audit RGPD, analytics coût.

```sql
CREATE TABLE llm_calls (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  purpose       TEXT NOT NULL,             -- GENERATE_PLAN | WEEKLY_UPDATE | CHAT | SANITIZE
  provider      TEXT NOT NULL,             -- ANTHROPIC
  model         TEXT NOT NULL,             -- claude-sonnet-4-6 …
  tokens_in     INT NOT NULL,
  tokens_in_cached INT NOT NULL DEFAULT 0, -- part du prompt caché (cache hits)
  tokens_out    INT NOT NULL,
  cost_micros   BIGINT NOT NULL,           -- coût en micro-euros (10^-6 €) pour éviter les floats
  duration_ms   INT NOT NULL,
  status        TEXT NOT NULL,             -- OK | ERROR | TIMEOUT | REJECTED_BUDGET
  error_code    TEXT,
  prompt_hash   TEXT NOT NULL,             -- SHA256 du prompt canonique, pour dedup analytics
  request_id    TEXT,                      -- corrélation avec la requête HTTP
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON llm_calls (user_id, created_at DESC);
CREATE INDEX ON llm_calls (created_at DESC);  -- rapports agrégés
```

**Le contenu du prompt N'EST PAS stocké** — voir §08 sur les données santé. On stocke uniquement le hash + les métadonnées. En cas de besoin de debug, un flag `LLM_DEBUG_STORE_PROMPTS=true` (jamais en prod par défaut) active un stockage temporaire séparé, purgé à 24 h.

### `usage_ledger` — quotas append-only

```sql
CREATE TABLE usage_ledger (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  period_month  DATE NOT NULL,             -- 1er du mois
  kind          TEXT NOT NULL,             -- CHAT_MESSAGE | WEEKLY_UPDATE | PLAN_GENERATION
  amount        INT NOT NULL DEFAULT 1,
  cost_micros   BIGINT NOT NULL DEFAULT 0,
  llm_call_id   UUID REFERENCES llm_calls(id),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON usage_ledger (user_id, period_month);
```

Consultation quota courant :
```sql
SELECT kind, SUM(amount) FROM usage_ledger
 WHERE user_id = :uid AND period_month = date_trunc('month', now())
 GROUP BY kind;
```

### `garmin_credentials` (V2)

```sql
CREATE TABLE garmin_credentials (
  user_id       UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  access_token_enc  BYTEA NOT NULL,        -- chiffré AES-GCM (KMS)
  refresh_token_enc BYTEA NOT NULL,
  expires_at    TIMESTAMPTZ NOT NULL,
  scopes        TEXT[] NOT NULL,
  connected_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Voir §08 sur le chiffrement au repos.

## Migrations Liquibase

Un changeset par table (fichiers séparés) + un master `db/changelog/db.changelog-master.yaml` qui les inclut. Convention de nommage :
`db/changelog/2026/01/001-create-users.yaml`, ordre lexicographique.

Les changesets qui modifient JSONB (ex. contrat `plan`) sont **additifs uniquement** — jamais de migration destructive sur JSONB, le contrat évolue via versioning applicatif (`plan.schemaVersion`).

## Volumétrie estimée

Hypothèse V1 : 1 000 utilisateurs actifs, 3 séances/semaine.

| Table | Taille 1 an |
|---|---|
| activities | ~150 k lignes / 100 Mo |
| llm_calls | ~200 k lignes / 30 Mo |
| chat_messages | ~500 k lignes / 100 Mo |
| prepas | ~1 500 lignes / 5 Mo (JSONB inclus) |

Aucun besoin de sharding V1/V2. Backup Postgres nightly + WAL archiving suffisent.
