# 07 — Authentification, plans tarifaires, quotas & rate limiting

## Authentification

### Choix : JWT stateless + refresh rotatif

- **Access token JWT HS256**, TTL 15 min, claims : `sub` (userId), `plan`, `role`, `iat`, `exp`, `jti`.
- **Refresh token opaque** (UUID v7 signé), TTL 30 j, **stocké en Redis** avec `{userId, hash, familyId}`. Rotation à chaque usage — si un refresh déjà consommé revient → révocation de toute la famille (détection de vol de token).
- **Blocklist des access token** après logout via Redis (TTL = TTL access token restant).

### Endpoints (résumé, détails §03)

`POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `POST /auth/verify-email`, `POST /auth/forgot-password`, `POST /auth/reset-password`.

### Password policy

- Argon2id (paramètres OWASP 2025 : m=64MB, t=3, p=1).
- Min 10 caractères, refus des top-1000 mots de passe compromis (liste HIBP embarquée en release, mise à jour trimestrielle).
- Reset : token à usage unique, TTL 1 h, envoyé par email, invalidé au premier usage.

### Vérification email

- Obligatoire pour utiliser le chat coach et générer un plan (empêche l'abus quota anonymement).
- Token à usage unique en base, TTL 48 h.

## Plans tarifaires

| Plan | Prix | Prépas simult. | Import CSV | Garmin API (V2) | Chat coach / mois | Weekly update / mois | Génération plan |
|---|---|---|---|---|---|---|---|
| **Free** | 0 € | 1 | ✅ | ❌ | 20 messages | 4 | 1 (à l'inscription) |
| **Pro** | 7,90 €/mois | 3 | ✅ | ✅ | 300 messages | 20 | 6 |

Les nombres sont des **quotas mensuels** (fenêtre glissante `date_trunc('month', now())`). Les cotisations Free sont dimensionnées pour :
- Un usage réel raisonnable (une question / 1,5 j).
- Un coût LLM plafond de ~0,15 €/mois (voir §05).

### Décrément

À chaque appel LLM réussi → écriture dans `usage_ledger` :
- `WEEKLY_UPDATE` → 1 unité.
- `CHAT_MESSAGE` → 1 unité par message user (les follow-ups streaming ne comptent pas).
- `PLAN_GENERATION` → 1 unité.
- `SANITIZE` → **jamais** débité à l'utilisateur (coût opérationnel absorbé).

Consultation : matérialisée à la demande, pas de compteur transactionnel (élimine le risque de contention en écriture).

### QuotaGuard

```java
public interface QuotaGuard {
  void assertAvailable(UUID userId, QuotaKind kind);   // 429 si dépassé
  void commit(UUID userId, QuotaKind kind, long costMicros, UUID llmCallId);
}
```

- `assertAvailable` **avant** l'appel LLM.
- `commit` **après**, dans la même transaction que la persistance du `llm_call`.
- Si `commit` échoue post-appel LLM : on **ne rembourse pas** (l'appel a coûté), on log un warning haut niveau.

### Downgrade / cancellation

- Downgrade Pro → Free : effectif à la fin de la période courante. Les prépas surnuméraires passent en lecture seule (pas de suppression automatique).
- Cancellation : idem, plus mail de confirmation.

## Rate limiting (hors quotas mensuels)

Protection anti-abus court terme, indépendante des quotas. **Bucket4j + Redis** :

| Ressource | Limite | Fenêtre |
|---|---|---|
| Toutes routes `/api/*` par IP | 300 req | 1 min |
| `/auth/login` par email | 5 tentatives | 15 min (puis lockout) |
| `/auth/register` par IP | 5 req | 1 h |
| `/coach/**` par user | 30 req | 1 min (anti-boucle infinie front) |
| Upload CSV par user | 3 | 5 min |

Dépassement → **429** avec `Retry-After` header.

Configurables via `application.yml`, valeurs par défaut ci-dessus, overridables par env.

## Billing (V2)

- Provider : **Stripe** (préféré à Paddle pour la maturité API Java).
- Modèle : abonnement mensuel, pas de proraté (Free ↔ Pro instant, calcul Stripe standard).
- Webhook Stripe → `/api/v1/billing/stripe/webhook` → maj `subscriptions.status`.
- Portail client : redirection Stripe Customer Portal (aucune UI de paiement à construire).

## Impersonation admin

Sur `POST /admin/users/{id}/impersonate` → renvoie un access token spécial avec claim `impersonatedBy=<adminId>` + `exp=1h`. Chaque appel LLM sous impersonation est **loggé avec un flag** et **ne débite pas** le quota de l'utilisateur cible.

## Résumé sécurité auth

- Aucune session serveur (stateless).
- Refresh token = seule ligne de vulnérabilité longue durée → stockage httpOnly V2, rotation famille.
- Login / reset : rate limit strict.
- Verif email = anti-fraude quota.
- Password : Argon2id, HIBP.
