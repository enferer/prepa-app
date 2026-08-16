# 10 — Roadmap & jalons d'implémentation

> Ordre proposé pour Claude Code. Chaque jalon = une PR livrable et démontrable, pas un big bang. Le principe : **on doit pouvoir utiliser l'app dès M2**, ensuite on ajoute des couches.

## M1 — Fondations (semaines 1–2)

**But** : squelette back + front + CI, sans métier.

- Repo monorepo : `api/` (Spring Boot), `web/` (Vue 3), `infra/` (docker-compose).
- Docker Compose local : Postgres 16, Redis 7.
- Liquibase : changesets `users`, `subscriptions`, migrations idempotentes.
- Spring Boot skeleton : config, actuator, health, exception handler, `RequestIdFilter`, logs JSON.
- Vue 3 skeleton : router, Pinia, Tailwind, `http.ts` avec intercepteur JWT.
- CI GitHub Actions : lint + tests + build image + push registry.
- Environnements local + staging déployés.

**Definition of done** : `curl /actuator/health` → 200 en staging, front vide déployé.

## M2 — Auth + comptes (semaines 3–4)

**But** : un utilisateur peut créer un compte et se connecter.

- Endpoints `/auth/*` (register, login, refresh, logout, verify-email, forgot/reset password).
- Argon2id, HIBP check, rate limit login.
- JWT + refresh rotatif Redis.
- Envoi email (SendGrid/Mailjet — provider à décider).
- Front : `LoginView`, `RegisterView`, `VerifyEmailView`, guard router.
- Store `auth` complet.
- Table `audit_events`.

**Definition of done** : parcours signup → verif email → login → dashboard vide.

## M3 — Prépa manuelle + import CSV (semaines 5–7)

**But** : un user importe son CSV et voit ses activités.

- Table `prepas`, `activities` + migrations.
- Endpoints `/prepas` CRUD (sans génération LLM — création directe avec un `objectifs.json` posté).
- `garmin.CsvParser` (Java, port de `build_data.py`) + `ActivityNormalizer` + `ActivityDedupService`.
- Endpoint `/prepas/{id}/activities/import-csv`.
- Front : `ImportCsvDropzone`, `DashboardView` (minimal), `PlanView` (lecture seule sur `plan.json` fourni manuellement).
- Un utilisateur admin peut créer une prépa fictive via un endpoint dédié pour tester.

**Definition of done** : import du CSV existant `profiles/thibaut/prepas/marathon-2026-10/data/garmin.csv` → liste d'activités visible dans le front.

## M4 — LLM Orchestrator + génération de plan (semaines 8–11) — **cœur technique**

**But** : le LLM génère un plan à partir d'un onboarding.

- Module `llm/` complet : `LlmClient`, `AnthropicClient`, `PromptBuilder`, `PromptRegistry`, `ContextCompactor`, `InputSanitizer`, `TokenBudget`, `LlmOrchestrator`, `ResponseValidator`, `LlmCallLog`.
- Prompts versionnés (`coach-system-v1.md`, `plan-generation-v1.md`) — dérivés de `coach/COACH.md`.
- Table `llm_calls`, `usage_ledger`.
- `QuotaGuard` + plans Free/Pro (hardcoded, pas de Stripe).
- Endpoint `/prepas` avec génération LLM asynchrone (jobs + polling).
- Front : `OnboardingWizard` complet, écran d'attente pendant génération.
- Tests : validation stabilité prompt système (hash snapshot), tests contrat sur `ResponseValidator`, mocks LLM en CI.
- Métriques LLM Micrometer + dashboard Grafana staging.

**Definition of done** : un user peut s'inscrire, faire l'onboarding, obtenir un plan complet généré, coût ≤ 0,05 € par génération.

## M5 — Weekly update + Chat coach (semaines 12–14)

**But** : le coach devient interactif.

- Endpoint `/prepas/{id}/weekly-update` (async job).
- Table `coach_reports`.
- Chat coach : tables `chat_sessions`, `chat_messages`, endpoints associés, streaming SSE.
- Prompts `weekly-update-v1.md`, extension `coach-system-v1.md` pour le mode chat.
- Front : `ChatView`, `QuestionSuggestions` (chips canoniques).
- Cache prompt caching Anthropic vérifié en prod, dashboard cache hit ratio.

**Definition of done** : après import CSV, l'utilisateur clique "Faire le point de la semaine" → reçoit un rapport + plan mis à jour ; peut poser 3 questions successives dont 2 tapent le cache.

## M6 — Journal, stats, réglages, finitions (semaines 15–16)

- `/prepas/{id}/journal` (upsert par semaine), `JournalView`.
- Endpoints `GET /me`, `PATCH /me`, `DELETE /me` + export RGPD.
- `StatsView` (volume hebdo, allures moyennes, tendances FC).
- `SettingsView` (mdp, timezone, langue, suppression compte, quotas visibles).
- Notifications email : semaine ratée, weekly update disponible.

**Definition of done** : parcours utilisateur complet sans intervention support.

## M7 — Billing Stripe (semaines 17–18)

- Intégration Stripe : produits, checkout, portail client, webhooks.
- Table `subscriptions` reliée à Stripe, downgrade/upgrade instantanés.
- Front : page tarifs, CTA upgrade sur toast `QUOTA_EXCEEDED`.
- Tests E2E paiement en environnement Stripe test.

## M8 — Garmin API (V2, trimestre suivant)

- Accord Garmin signé.
- Endpoints OAuth `/garmin/connect/start`, `/callback`, `/webhooks/activities`.
- `GarminApiClient`, `GarminIngestJob`, `GarminBackfillJob`, `ActivityReconciliationJob`.
- Chiffrement AES-GCM des tokens (`garmin_credentials`).
- Front : bouton "Connecter Garmin" dans Réglages, statut de sync.

## M9+ — Hardening & croissance

- Playwright E2E (parcours complets).
- OpenTelemetry tracing.
- Notifications push (PWA).
- Multi-langue (en, es).
- Export PDF plan de semaine.

## Ordre d'attaque conseillé à Claude Code

Pour chaque jalon :
1. Lire les fichiers `05-*.md` et `SCHEMA.md` avant tout code LLM.
2. Ouvrir une branche `feat/M<n>-<slug>`.
3. Poser les migrations Liquibase **avant** le code Java (schéma → repo → service → controller).
4. Écrire les tests d'intégration avec Testcontainers dès l'étape service.
5. Toujours vérifier la sanitize + tokens sur les endpoints LLM (dashboard staging).
6. Une PR = un jalon, une revue humaine avant merge.

## Ce qui n'est PAS dans la roadmap V1/V2 (rappel)

- App mobile native.
- Coach spécifique trail/triathlon.
- Multi-provider LLM (l'abstraction est là, l'implémentation viendra).
- Feed social / partage entre utilisateurs.
- Coach voix.
