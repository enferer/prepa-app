# 01 — Architecture technique

## Vue d'ensemble

```
┌──────────────────┐     HTTPS/JSON      ┌──────────────────────────────┐
│  Front SPA Vue3  │ ──────────────────▶ │  Back Spring Boot (REST)     │
│  (Vite, Pinia)   │ ◀────────────────── │  Java 21, WebMVC, JPA        │
└──────────────────┘                     └───────┬──────────────────────┘
                                                 │
                    ┌────────────────────────────┼───────────────────────────────┐
                    │                            │                               │
                    ▼                            ▼                               ▼
        ┌────────────────────┐      ┌─────────────────────────┐     ┌────────────────────┐
        │  PostgreSQL 16     │      │  LLM Orchestrator       │     │  Garmin Adapter    │
        │  (relationnel      │      │  ├── PromptBuilder      │     │  ├── CsvParser (V1)│
        │   + JSONB pour     │      │  ├── ContextCompactor   │     │  └── OAuth+API (V2)│
        │   objectifs/plan)  │      │  ├── TokenBudget        │     └────────────────────┘
        └────────────────────┘      │  └── LlmClient (impl.   │
                                    │       Anthropic Claude) │
                                    └──────────┬──────────────┘
                                               │
                                               ▼
                                    ┌──────────────────────┐
                                    │  Redis               │
                                    │  ├── cache prompts   │
                                    │  ├── rate limiting   │
                                    │  └── sessions chat   │
                                    └──────────────────────┘
```

## Stack — choix arrêtés

### Backend
- **Java 21** (LTS), records, pattern matching, virtual threads pour la couche HTTP → LLM (I/O bound, on veut du throughput sans thread pool massif).
- **Spring Boot 3.3+** — starter web, security, validation, data-jpa, actuator.
- **Spring Security 6** — filter JWT stateless, endpoints publics/protégés déclarés par matcher.
- **JPA / Hibernate** avec **PostgreSQL** — voir §02 pour la stratégie JSONB.
- **Liquibase** pour les migrations (préféré à Flyway pour la lisibilité YAML/XML sur les migrations JSONB).
- **MapStruct** pour DTO ↔ Entity (évite les mappers manuels bug-prones).
- **Micrometer + Prometheus** — métriques (dont métriques LLM custom).
- **Testcontainers** (Postgres + Redis) pour les tests d'intégration.

### Frontend
- **Vue 3** (Composition API + `<script setup>`) — TypeScript strict.
- **Vite** — build/dev.
- **Pinia** — state management (stores : `auth`, `prepaActive`, `plan`, `chat`, `quotas`).
- **Vue Router** — auth guards par meta `requiresAuth`.
- **TailwindCSS** — cohérent avec la vue actuelle simple, permet de livrer vite un design propre.
- **Axios** avec interceptor JWT + refresh + gestion 429 (quotas).
- **Vitest + Vue Test Utils** — tests unit / composants.

### Infra
- **PostgreSQL 16** — DB primaire.
- **Redis 7** — cache + rate limiting (bucket4j Redis backend) + session chat court terme.
- **Docker Compose** en dev, **conteneurs OCI** en prod (au choix opérateur : Scaleway / OVH / AWS ECS).
- **Nginx** en reverse proxy (TLS, gzip, static assets front).
- **GitHub Actions** — CI : lint + tests + build image + push registry.

## Pourquoi PostgreSQL et **pas** MongoDB

L'utilisateur pressent que le projet est "adapté à du NoSQL". C'est partiellement vrai (les documents `plan.json` sont typiquement des documents imbriqués), mais :

- **90 % des données sont relationnelles** : `users`, `subscriptions`, `usage_ledger` (quotas LLM), `activities` (indexées par athlète + date, agrégations SQL fréquentes), `journal_entries`. Ça se gère mille fois mieux en SQL, avec des transactions ACID (facturation, décrément quotas).
- **Les 10 % réellement "document" (`objectifs`, `plan`) tiennent parfaitement en JSONB PostgreSQL** — on garde la souplesse de schéma d'un document store (le contrat `SCHEMA.md` peut évoluer sans migration) + les index GIN si besoin, tout en gardant **une seule base**.
- Une seule base → une seule stratégie de backup, un seul point de monitoring, un seul driver, aucun problème de cohérence cross-store. La règle par défaut sur un SaaS de cette taille : **une base relationnelle sauf preuve du contraire**.
- L'utilisateur est plus à l'aise en SQL → moins de friction opérationnelle.

Recommandation : **PostgreSQL, JSONB pour les documents plan/objectifs/report**, pas de Mongo.

## Pourquoi Anthropic Claude comme LLM V1

- Qualité de raisonnement structuré (adaptation d'un plan = raisonnement multi-étapes sur données structurées) — mieux servi par Claude sur nos benchs internes.
- **Prompt caching Anthropic** — natif, TTL 5 min ou 1 h. Sur notre usage (système prompt "coach" massif + docs de référence stables) le cache réduit de 60–80 % le coût des tokens d'entrée. **C'est le levier principal de maîtrise du coût** (voir §05).
- Fenêtre de contexte large (200 k tokens) — laisse de la marge sans complexité de RAG en V1.

L'interface `LlmClient` reste provider-agnostique — remplaçable par OpenAI / Mistral / Bedrock plus tard.

**Modèles ciblés** :
- **Haiku 4.5** (`claude-haiku-4-5`) pour les tâches courtes (validation d'une séance, résumé hebdo, sanitize).
- **Sonnet 4.6** (`claude-sonnet-4-6`) pour la génération de plan et l'adaptation multi-semaines.
- Opus **jamais** en runtime utilisateur — coût prohibitif à l'échelle.

Ces modèles sont configurables par variable d'environnement (`LLM_MODEL_FAST`, `LLM_MODEL_SMART`) — ne pas hardcoder.

## Modules du backend (packages Java)

```
com.prepacoach.api
├── auth/           JWT, refresh, OAuth Google
├── user/           User, profile, subscription
├── prepa/          Prepa (objectifs, plan) — CRUD + génération
├── activity/       Activités (import CSV, futur Garmin API), agrégations
├── journal/        Journal hebdo texte libre
├── coach/          Endpoints "ask coach", "weekly update" — orchestration LLM
├── llm/            LlmClient, PromptBuilder, ContextCompactor, TokenBudget
├── garmin/         CsvParser (V1), GarminConnectClient (V2)
├── billing/        Plans (Free/Pro), Stripe (V2)
├── quota/          UsageLedger, rate limiting
├── notification/   Email (SendGrid), push (V2)
└── infra/          Config, Redis, security config, exception handler
```

Chaque package est un **module fonctionnel autonome** — pas de dépendance croisée sauvage. Règle : `coach/` peut dépendre de `llm/` `prepa/` `activity/` `journal/`, mais l'inverse est interdit.

## Environnements

| Env | URL | DB | Provider LLM |
|---|---|---|---|
| local | http://localhost:5173 (front) / :8080 (back) | Postgres container | Anthropic prod (mode dry-run activable via flag) |
| staging | staging.prepacoach.app | Postgres managé | Anthropic prod, budget capé |
| prod | app.prepacoach.app | Postgres managé + replica | Anthropic prod |

Aucun code de mock LLM en prod. Le mode `LLM_DRY_RUN=true` renvoie une réponse canned + log détaillé du prompt qui *serait* envoyé — utile pour tester la sanitize sans brûler des tokens.
