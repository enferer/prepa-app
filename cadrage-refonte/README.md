# Cadrage — Refonte `prepa-app` en application SaaS

> Documentation de cadrage fonctionnel + technique destinée à Claude Code pour l'implémentation d'une nouvelle version de `prepa-app`, sous forme d'application web multi-utilisateurs avec back Java / Spring Boot, front Vue.js, LLM orchestré côté back et intégration Garmin.

## Objectif de la refonte

L'application actuelle (`prepa-app`) est un **outil mono-utilisateur, pilotée par Claude Code en local** : les données vivent dans des fichiers JSON/CSV, la "vue" est une page statique et **le coach = Claude lui-même** exécutant des skills. C'est très efficace mais réservé à un utilisateur unique qui maîtrise Claude Code.

La refonte cible :

- Une **application SaaS** hébergée, avec inscription, comptes, plusieurs athlètes indépendants.
- **Import Garmin en deux temps** : CSV manuel (V1), puis OAuth Garmin Connect (V2).
- **Un LLM en boîte noire côté back** : le front n'appelle jamais l'IA directement. Le back construit des prompts **compacts, normés, déterministes** pour minimiser drastiquement la consommation de tokens.
- **Une UX très simple** côté athlète : "Où j'en suis ? Que dois-je faire ? Ai-je un souci ?" — le reste doit rester invisible.
- Un **système de quotas** par plan (Free / Pro) pour maîtriser le coût LLM par utilisateur.

## Structure de la documentation

| Fichier | Contenu |
|---|---|
| [00-vision-perimetre.md](00-vision-perimetre.md) | Vision produit, personas, périmètre V1 vs V2, hors-scope |
| [01-architecture.md](01-architecture.md) | Stack, diagrammes de composants, choix techniques justifiés |
| [02-domain-model.md](02-domain-model.md) | Modèle de domaine, schéma PostgreSQL + JSONB, migrations |
| [03-backend-spring.md](03-backend-spring.md) | Modules Spring Boot, endpoints REST, DTOs, services |
| [04-frontend-vue.md](04-frontend-vue.md) | Architecture SPA Vue 3, écrans, composants, state |
| [05-llm-orchestration.md](05-llm-orchestration.md) | **Cœur du sujet** — sanitize des requêtes, prompt engineering, format compact, cache, budget de tokens |
| [06-garmin-integration.md](06-garmin-integration.md) | Parser CSV (V1), intégration OAuth Garmin Connect + Activity API (V2), webhooks |
| [07-auth-quotas-plans.md](07-auth-quotas-plans.md) | Auth JWT, plans tarifaires, rate limiting, quotas de tokens |
| [08-security-rgpd.md](08-security-rgpd.md) | Sécurité, secrets, RGPD, données santé |
| [09-observability.md](09-observability.md) | Logs structurés, métriques (dont coût LLM par user), tracing |
| [10-roadmap-jalons.md](10-roadmap-jalons.md) | Jalons M1 → M6, ordre d'implémentation |

## Principes directeurs

Lire dans cet ordre :

1. **Le coach existant est le brief produit.** Toute la logique métier (allures, blocs, adaptations, protocoles douleur) est déjà écrite dans [`../coach/COACH.md`](../coach/COACH.md). La refonte **ne réécrit pas cette méthodologie** — elle la porte dans un système prompt versionné et sanctuarisé côté back (§05).
2. **Le contrat de données existant est le point de départ.** [`../coach/SCHEMA.md`](../coach/SCHEMA.md) décrit `objectifs.json` et `plan.json` — on **conserve ces structures** comme documents JSON stockés en JSONB, on ne les éclate pas en tables relationnelles.
3. **Token-first.** Chaque interaction avec le LLM doit passer par un **PromptBuilder** qui : (a) réduit le contexte au strict nécessaire, (b) sérialise en un format compact documenté, (c) budgétise et journalise les tokens consommés. Aucune requête utilisateur ne part "brute" vers le LLM.
4. **Simplicité côté athlète.** Le front n'expose **jamais** de champs techniques (JSON, allures en secondes, VMA en pourcentage) sans traduction. Les seules actions offertes sur le dashboard : *Importer mes séances* — *Poser une question à mon coach* — *Voir mon plan*.
