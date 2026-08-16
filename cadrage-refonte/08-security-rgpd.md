# 08 — Sécurité & RGPD

## Classification des données

| Catégorie | Exemples | Sensibilité |
|---|---|---|
| Identifiants | email, nom, hash mdp | Personnelles |
| Santé / activité | FC, blessures, journal | **Sensibles (santé, art. 9 RGPD)** |
| Techniques | logs, request_id, IP | Personnelles |
| Paiement | infos CB | **Sensibles** — jamais chez nous (Stripe) |

L'ensemble `blessures`, `journal.mood`, `journal.content`, `activities.avg_hr` relève des **données de santé**. Le traitement se justifie par le **consentement explicite** (checkbox à l'inscription, dissociée des CGU, avec libellé clair).

## Base légale et information

- **Consentement explicite** au traitement des données de santé, retirable à tout moment (page Réglages).
- **CGU + politique de confidentialité** claires, en fr, mentionnant : sous-traitants (Anthropic, Stripe, Garmin, hébergeur), transferts hors UE (Anthropic US → clauses contractuelles types).
- **Registre des traitements** tenu à jour (obligation art. 30 RGPD).
- DPO désigné si équipe > 5 pers ou > 5 000 users.

## Droits utilisateur

Endpoints dédiés :
- `GET /api/v1/me/export` → job asynchrone, produit un ZIP (JSON de toutes les données de l'user) — livré par email sous 24 h.
- `DELETE /api/v1/me` → soft-delete immédiat, job de purge à J+30 (fenêtre pour se raviser). Purge dure sur toutes les tables sauf `llm_calls` qui garde uniquement `id`, `created_at`, `cost_micros`, `purpose` (agrégats anonymisés — base légale : intérêt légitime facturation/comptabilité).

## Chiffrement

### En transit
- HTTPS obligatoire partout, TLS 1.3, HSTS 1 an, redirect HTTP → HTTPS.
- Cert Let's Encrypt via Nginx, renouvellement auto.
- Certificats internes pour appels DB (PostgreSQL SSL, sslmode=verify-full en prod).

### Au repos
- Disques hébergeur chiffrés (BYOK ou géré selon provider).
- **Colonnes sensibles chiffrées applicativement** en plus :
  - `garmin_credentials.access_token_enc`, `refresh_token_enc` — AES-GCM, clé montée depuis un KMS (AWS KMS / Scaleway Secret Manager).
  - `journal_entries.content` — chiffrement colonne (V2, envelope encryption).
- Backups DB chiffrés + testés (restore trimestriel).

### Secrets
- Aucun secret en base ni en repo.
- Injection par variables d'env, montées depuis un secret manager (KMS ou HashiCorp Vault).
- Rotation trimestrielle pour les secrets HMAC/JWT.

## Logs — règle d'or

**Les logs ne contiennent JAMAIS :**
- Le contenu des messages de chat.
- Le contenu du journal.
- Les tokens JWT / refresh / Garmin.
- Les prompts LLM complets.
- Les emails en clair (masqué `t***@wesyn.fr`).

Ils contiennent :
- `requestId` (UUID par requête HTTP).
- `userId` (UUID, pas email).
- Code d'erreur, durée, statut HTTP.
- Métadonnées LLM : `purpose`, `model`, `tokensIn`, `tokensOut`, `costMicros`, `promptHash`.

Le `promptHash` (SHA256 canonique) permet **la corrélation sans stockage du contenu**.

Un `LogSanitizer` (filter Logback) est branché sur `MDC` : toute clé matchant `password|token|content|message|email` est masquée.

## Envoi à Anthropic — conformité

- **Adresse du DPA Anthropic signé** (Data Processing Agreement) — pré-requis avant mise en prod.
- Les données envoyées : format compact (§05), aucune PII directe. Le prénom peut être présent — évaluer si on l'anonymise (V1 : envoi du prénom accepté, documenté).
- Header `anthropic-metadata: {"user_id": "<hash>"}` pour permettre le rate limiting Anthropic sans révéler l'identité.
- Option `no_training` activée sur l'API (les données ne servent pas à entraîner les modèles Anthropic).

## Sécurité applicative

### OWASP top 10 — check-list

- **Injection SQL** : uniquement Hibernate + Spring Data (queries paramétrées). Aucune concaténation.
- **XSS** : Vue échappe par défaut (`{{ }}`). Interdiction stricte de `v-html` (règle ESLint bloquante). Backend : jamais de HTML.
- **CSRF** : stateless JWT → non applicable sur `/api`. Cookies limités au refresh (V2 SameSite=Strict).
- **Access control** : chaque endpoint valide `resource.userId == authenticatedUserId`. Test **systématique** de contournement dans les tests d'intégration.
- **Deserialization** : Jackson avec `FAIL_ON_UNKNOWN_PROPERTIES=true` et pas de polymorphic default typing.
- **SSRF** : `GarminApiClient` cible un host fixé, aucun URL user-controlled n'est fetch.
- **Fichiers** : upload multipart limité 10 Mo, magic bytes vérifiés, jamais exécuté.

### Content Security Policy

Front sert avec CSP strict :
```
default-src 'self';
script-src 'self';
style-src 'self' 'unsafe-inline';   // Tailwind runtime none, mais safeguard
img-src 'self' data:;
connect-src 'self' https://api.prepacoach.app;
frame-ancestors 'none';
```

### Dépendances

- **Renovate** automatique (weekly PR).
- **OWASP Dependency-Check** en CI, échoue sur CVE HIGH+.
- **npm audit** en CI front.

### Tests de sécurité

- Tests d'intégration : pour chaque endpoint protégé, un test qui tente d'accéder à la ressource d'un autre user → 403.
- Suite automatisée `zap-baseline` en staging (OWASP ZAP).
- Revue de sécurité avant chaque release majeure (skill `/security-review`).

## Rétention

| Donnée | Durée |
|---|---|
| Compte utilisateur actif | Illimitée (tant qu'actif) |
| Compte inactif (0 login) | Rappel à 24 mois, suppression à 30 mois |
| Compte supprimé | Purge à J+30 |
| Logs applicatifs | 90 jours |
| `llm_calls` (métadonnées) | 24 mois puis anonymisé |
| Backups | 35 jours (roulant) |

## Incidents

Procédure documentée séparément (`docs/incident-response.md` à écrire) :
1. Détection (alerting §09).
2. Confinement.
3. Notification CNIL sous 72 h si fuite de données perso confirmée.
4. Notification utilisateurs affectés si risque élevé.
5. Post-mortem sans blâme, actions correctives.
