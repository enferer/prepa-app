# 05 — Orchestration LLM & optimisation des tokens

> **Le cœur technique du projet.** Toute la valeur (qualité coach) et tout le coût (marge unitaire) se jouent ici. Aucun compromis à faire sur la rigueur des sections ci-dessous.

## Principes non négociables

1. **Aucun appel LLM depuis le front.** Le front parle à `/api/v1/coach/*`, jamais à Anthropic. Il ne connaît même pas le nom du provider.
2. **Chaque appel LLM passe par le `LlmOrchestrator`.** Il n'y a **qu'un point d'entrée** dans le code — pas d'appel direct à `LlmClient` depuis un service métier. Cela garantit : logging, budget, cache, retry, sanitize systématiques.
3. **Aucune requête utilisateur brute n'est envoyée au LLM.** Elle est d'abord *sanitized* et *canonicalized* (cf. plus bas).
4. **Le contexte est compact et documenté.** Un format sérialisation propriétaire à nous (voir `Format compact`), documenté dans le système prompt, remplace tout envoi de JSON complet.
5. **Le prompt système est versionné, immuable, et cacheable.** Il ne contient aucune donnée utilisateur — il ne change **qu'entre deux releases**. C'est la condition sine qua non pour bénéficier du prompt caching Anthropic.

## Architecture

```
Controller
   │
   ▼
CoachService
   │
   ▼
LlmOrchestrator ── purpose (CHAT | WEEKLY_UPDATE | GENERATE_PLAN)
   │
   ├─► QuotaGuard.check(user, purpose)      → 429 si dépassé
   ├─► InputSanitizer.clean(userMessage)    → retire PII, normalise, coupe
   ├─► PromptBuilder.build(purpose, ctx)    → assemble system + context + user
   ├─► ContextCompactor.compact(ctx)        → réduit au minimum nécessaire
   ├─► TokenBudget.assertBelow(prompt)      → 429 si estimation dépasse le cap
   ├─► LlmCache.get(promptHash)             → réponse déjà connue ? (rare mais utile)
   ├─► LlmClient.send(prompt)               → provider Anthropic
   ├─► ResponseValidator.parse(json)        → schéma stricte, rejette + retry si invalide
   ├─► LlmCallLog.persist(...)              → traces + coût
   └─► QuotaGuard.commit(user, purpose, cost)
```

## Anatomie d'un prompt

Structure de tous nos prompts, avec les blocs qui **doivent** être placés dans cet ordre pour maximiser le cache hit Anthropic (les blocs stables **en premier**) :

```
┌─────────────────────────────────────────────┐
│  SYSTEM BLOCK (cache_control: ephemeral)   │  ← identique pour TOUS les users
│  - Identité coach (dérivé de COACH.md)      │  ← ne change qu'entre releases
│  - Format compact (spec ci-dessous)         │
│  - Règles de sortie (JSON schema attendu)   │
├─────────────────────────────────────────────┤
│  KNOWLEDGE BLOCK (cache_control: ephemeral)│  ← identique pour tous
│  - Tables allures VDOT                       │
│  - Protocoles douleur                        │
├─────────────────────────────────────────────┤
│  USER CONTEXT BLOCK (jamais caché)          │  ← spécifique à l'utilisateur
│  - Profil compact (âge, chrono cible, …)    │
│  - Prépa compacte                            │
│  - Activités compactes (fenêtre glissante)  │
│  - Journal récent (résumé)                   │
├─────────────────────────────────────────────┤
│  TASK BLOCK                                 │  ← spécifique à la requête
│  - Instruction précise                       │
│  - Question utilisateur sanitizée            │
└─────────────────────────────────────────────┘
```

Le **SYSTEM + KNOWLEDGE** est envoyé avec `cache_control: {"type": "ephemeral"}`. TTL 5 min par défaut (Anthropic), 1 h option premium. Sur nos volumes, un utilisateur qui pose 3 questions dans la même session paie le prompt système **une seule fois**, les 2 suivantes coûtent ~90 % moins cher côté tokens d'entrée.

**Ratio attendu prompt cache hit / miss** : > 60 % en régime établi. C'est un SLO produit (§09).

## Format compact — spec

Le format compact est le langage dans lequel on décrit la prépa et les activités au LLM. Il est **documenté dans le prompt système** pour que le modèle sache le lire. C'est un texte semi-tabulaire :

```
ATH: T.34M chrono4h00 rp10=42:15 rpSemi=1:35:20 dispo=4j/sem
PREPA: marathon 2026-10-25 sem15/15 bloc=affutage
ALLURES: EF=6:40 SL=6:20 AM=5:41 SEU=5:20 VMA=4:30

ACTIV(30j):
2026-07-12 EF 10km 6:35 h145
2026-07-13 SL 24km 6:22 h152 elev220
2026-07-15 SEU 12km 5:18 h168 [8x1000]
2026-07-17 EF 8km 6:41 h143
...

PLAN(sem14-15):
S14 vol=48 dech
  Lun EF 8
  Mar SEU 12 [5x1500]
  Jeu EF 10
  Sam SL 22
S15 vol=32 course
  Mer EF 6
  Sam MARATHON

JOURNAL(sem14):
"jambes lourdes mardi, mieux jeudi. mollet gauche tire léger."
```

### Règles de sérialisation

- Dates ISO courtes (`YYYY-MM-DD`).
- Allures format `m:ss` (jamais secondes).
- Distances entières en km, décimales seulement si < 10 km.
- FC en `h<bpm>`, dénivelé en `elev<m>`, préfixes uniques (le modèle apprend le mapping via le system prompt).
- **Aucune clé JSON** (`"distanceKm":`) — c'est là qu'on gagne 50 % des tokens vs. envoyer les JSON bruts.
- Statuts encodés en 1 lettre : `V` (validée) / `M` (manquée) / `A` (adaptée) / `-` (à venir).
- Lignes triées chronologiquement — le modèle repère les patterns temporels plus facilement.

### Comparatif coût

Sur un contexte type (30 activités + plan 15 semaines + journal) :
- **JSON brut** : ~11 000 tokens d'entrée.
- **Format compact** : ~2 200 tokens d'entrée.
- **Avec cache hit sur system+knowledge** : ~600 tokens facturés plein tarif + 8 000 tokens cachés (facturés ~10 %).

Réduction totale : facteur ~15 sur le coût par requête.

## `ContextCompactor` — quelles données envoyer

Règle générale : **le minimum utile à la tâche demandée**. Deux stratégies selon `purpose`.

### `purpose = CHAT`

Contexte inclus :
- Profil compact (5 lignes max).
- Prépa : bloc courant + 2 semaines avant / 2 après (pas tout le plan).
- Activités : **30 derniers jours** max (fenêtre glissante).
- Journal : **2 dernières semaines** uniquement.
- **Historique de la session** : les 6 derniers messages user+assistant (rolling).

Budget cible entrée (contexte utilisateur) : **~1 500 tokens**.

### `purpose = WEEKLY_UPDATE`

Contexte inclus :
- Profil compact.
- Prépa entière (compact — un plan 15 semaines fait ~800 tokens en compact).
- Activités : **7 derniers jours** en détail + agrégats hebdo des 4 semaines précédentes.
- Journal : semaine à évaluer + précédente.

Budget cible : **~2 500 tokens** entrée utilisateur.

### `purpose = GENERATE_PLAN`

Contexte inclus :
- Réponses complètes de l'onboarding (une seule fois).
- Aucune activité (l'utilisateur est nouveau).

Budget cible : **~1 000 tokens** entrée + **~4 000 tokens** sortie (le plan).

### Règle "fenêtre glissante" pour les activités

`ContextCompactor` propose 3 tailles de fenêtre : **7j**, **30j**, **90j+agrégats**. Il choisit en fonction du `purpose` et de la question sanitizée :
- Question contenant "hier", "cette semaine", "dernière séance" → fenêtre 7j.
- Question sur la tendance, la progression, "j'ai l'impression que" → 30j détails + 90j agrégats.
- Question sur "puis-je viser un chrono X" → 90j agrégats + records structurés.

Ce classifieur est un **petit prompt Haiku dédié** (`purpose=SANITIZE`), coûte ~300 tokens et permet d'économiser des milliers sur l'appel principal. C'est un investissement rentable dès la 1re question sur 3.

## `InputSanitizer`

Objectif : transformer la question utilisateur en une **question canonique** minimale.

Actions :
1. **Trim + collapse whitespace**.
2. **Longueur max** 500 caractères — au-delà, on demande au LLM Haiku de résumer avant.
3. **Retrait PII évidents** (email, numéros de téléphone) via regex — le coach n'en a pas besoin.
4. **Détection de langue** : rejette / traduit si != fr (V1 : rejette avec message clair).
5. **Détection intention** : mappe vers un des ~15 intents connus (`STATUS_WEEK`, `PACE_QUESTION`, `PAIN_REPORT`, `FEELING_TIRED`, `RECALIBRATE_GOAL`, `CHANGE_SCHEDULE`, …). Sur intent connu, on injecte un **préambule structuré** dans le user prompt qui aide le modèle à répondre au bon format ("L'utilisateur signale une douleur — applique le protocole COACH §4.douleur").
6. **Suggestion questions guidées** : les chips côté front envoient des questions **déjà canoniques**, ce qui court-circuite Sanitizer et fait économiser un appel LLM.

## `TokenBudget`

Estimation locale via un tokenizer client (`jtokkit` pour un ordre de grandeur — pas exact pour Claude mais suffisant à ±10 %). Deux caps :

- **Cap par requête** : 8 000 tokens d'entrée max pour un CHAT, 12 000 pour WEEKLY_UPDATE, 6 000 pour GENERATE_PLAN. Au-delà → on **compacte plus agressivement** (fenêtre plus petite, résumé journal). Si toujours > cap : `LlmBudgetRejectedException` → 429 avec message "Contexte trop large, contacte le support" (ne devrait quasi jamais arriver).
- **Cap par user par mois** : dérivé du plan (§07). Suivi via `usage_ledger`.

## `LlmClient` — interface

```java
public interface LlmClient {
  LlmResponse send(LlmRequest request);
  Flux<LlmChunk> stream(LlmRequest request);   // pour SSE chat
}

public record LlmRequest(
    String model,
    List<PromptBlock> blocks,     // ordonnés, avec cacheControl
    OutputFormat format,          // TEXT | JSON_SCHEMA(schema)
    int maxTokens,
    Double temperature,
    Duration timeout
) {}
```

Implémentation V1 : `AnthropicClient` (SDK officiel). Provider caché par variable d'env — permet swap.

## `ResponseValidator`

Pour `purpose=GENERATE_PLAN` et `WEEKLY_UPDATE`, la sortie est une **structure JSON** stricte (conforme au contrat `objectifs`/`plan` de SCHEMA.md + un `changes[]`). On demande au modèle un JSON via l'output format Anthropic, on parse, on valide contre un schéma Java (record + validation Bean).

Si invalide : **1 retry** avec un message d'erreur ciblé injecté ("Ta sortie précédente ne respecte pas le schéma : {erreur}. Renvoie uniquement le JSON conforme."). Au 2e échec → 502 + log détaillé.

Pour `purpose=CHAT` la sortie est du texte libre — pas de validation stricte, mais on rejette les réponses > 1 000 tokens (trop bavard = surcoût gratuit).

## Prompt caching — checklist opérationnelle

Pour ne PAS casser le cache Anthropic :
- Le contenu des blocs cachés doit être **byte-identique** entre les appels. Aucun timestamp, aucun UUID, aucun rendering dynamique.
- Le **modèle** doit être identique (`claude-sonnet-4-6` ≠ `claude-sonnet-4-6-20260210`). Pin explicite.
- L'**ordre** des blocs est immuable.
- Nouveau déploiement avec système prompt modifié → **le cache est invalidé** globalement pour ces users, prévoir un pic de coût le jour de la release.

Test unitaire : `SystemPromptStabilityTest` calcule le hash SHA256 du prompt système et échoue si != valeur snapshot (mise à jour explicite = décision de release).

## Fichiers de prompts

Dans `src/main/resources/prompts/` :

- `coach-system-v1.md` — le rôle coach (dérivé de `coach/COACH.md`, adapté au ton chat + inclut la spec du format compact).
- `plan-generation-v1.md` — instructions génération de plan initial + JSON schema attendu.
- `weekly-update-v1.md` — instructions rapprochement + JSON schema `{ report: {...}, changes: [...] }`.
- `sanitize-v1.md` — instructions classification d'intent + fenêtre.

Ces fichiers sont **loadés au démarrage** dans un `@Component PromptRegistry` (immutable, singleton). Test unitaire : chaque fichier est présent et non vide.

## Fallback dégradé

Si `LlmClient` échoue après retry ou timeout :
- CHAT → message générique "Ton coach est momentanément indisponible" + on ne débite pas le quota.
- WEEKLY_UPDATE → job en `FAILED`, l'utilisateur peut re-déclencher plus tard, aucun changement appliqué au plan.
- GENERATE_PLAN → **jamais** de plan par défaut. On demande à l'utilisateur de retenter.

## Budget cible par appel (aide au sizing)

| Purpose | Modèle | Tokens in (facturé) | Tokens in (cachés) | Tokens out | Coût estimé/appel* |
|---|---|---|---|---|---|
| SANITIZE | Haiku | ~300 | 0 | ~50 | < 0,001 € |
| CHAT | Sonnet | ~1 500 | ~7 500 (90 % remise) | ~400 | ~0,010 € |
| WEEKLY_UPDATE | Sonnet | ~2 500 | ~7 500 | ~1 500 | ~0,030 € |
| GENERATE_PLAN | Sonnet | ~1 000 | ~7 500 | ~4 000 | ~0,045 € |

*Ordre de grandeur, à réviser aux tarifs réels au moment de l'implémentation.

Combiné avec les quotas §07, cela donne un **coût plancher par user Free < 0,15 €/mois**, cible produit.
