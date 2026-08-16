# 06 — Intégration Garmin

Deux modes en séquence : **CSV manuel V1**, **OAuth Garmin Connect V2**. Même modèle cible côté `activities` — le second mode réutilise le pipeline de normalisation du premier.

## V1 — Import CSV

### Le CSV Garmin standard

Export depuis Garmin Connect Web → *Activités* → *Exporter au format CSV*. Format FR par défaut (séparateur `,`, décimales `,`, dates `JJ/MM/YYYY HH:MM`).

Colonnes typiques (variable selon locale et versions) :
```
Type d'activité, Date, Favori, Titre, Distance, Calories, Temps,
Fréquence cardiaque moyenne, Fréquence cardiaque maximum,
Allure moyenne, Meilleure allure, Élévation totale, ...
```

La logique existante dans `scripts/build_data.py` (`parse_garmin`, `duration_to_seconds`, `pace_to_seconds`, `parse_date`) est **la spécification de référence** — on la porte en Java dans `garmin.CsvParser`.

### Endpoint

```
POST /api/v1/prepas/{id}/activities/import-csv
Content-Type: multipart/form-data
Body: file=<csv>
```

Réponse :
```json
{
  "detected": 42,
  "imported": 39,
  "skippedDuplicates": 3,
  "warnings": [{ "row": 12, "reason": "Date invalide, ligne ignorée" }]
}
```

### Pipeline

```
CsvParser (stream ligne par ligne, ne charge pas tout)
   │
   ▼
ActivityNormalizer  →  Activity (record)
   │
   ▼
ActivityDedupService  →  filtre les (prepa_id, source, external_id) ou (prepa_id, started_at, distance, duration) déjà présents
   │
   ▼
ActivityRepository.saveAll (batch INSERT)
   │
   ▼
Event: ActivitiesImportedEvent(prepaId, count)  →  invalide caches, notifie widget dashboard
```

### Règles de dédup CSV

Le CSV ne fournit **pas** d'ID Garmin stable. On construit une clé de dédup logique :
```
key = sha256(prepa_id | started_at_iso | duration_sec | distance_m)
```
Stockée dans `activities.external_id` avec `source=GARMIN_CSV`. Ré-importer le même CSV = 0 doublon.

### Robustesse parser

- Encodage : détection UTF-8 vs UTF-8-BOM vs Windows-1252 (bibliothèque `juniversalchardet`).
- Séparateur : détection `,` vs `;` en lisant la première ligne d'en-tête.
- Lignes malformées → warning + skip, jamais throw global.
- Colonnes absentes → défaut null, jamais throw.
- Types "Musculation", "Natation en piscine", "Vélo intérieur" → mappés vers `STRENGTH`, `SWIM`, `BIKE` (tables de mapping en config, éditables sans redeploy).

### Sécurité upload

- **10 Mo max** (~50 000 activités, bien au-delà d'un usage normal).
- Content-Type MIME + magic bytes vérifiés (pas juste le nom).
- Fichier stocké **en mémoire uniquement**, jamais sur disque.
- Multipart limits Spring configurés bas.

## V2 — Garmin Connect API

### Aperçu

Garmin propose une **Health API** OAuth 2.0 pour applications tierces (accord commercial + revue technique nécessaire côté Garmin). Deux endpoints principaux :
- **Wellness API** — daily summaries.
- **Activity API** — activités détaillées (celle qui nous intéresse).
- **Webhooks** (Push Notification Service) — Garmin nous notifie des nouvelles activités par POST → on va les chercher.

Pré-requis produit avant implémentation :
1. Compte développeur Garmin approuvé.
2. Signature d'un accord (payant à partir d'un certain volume).
3. Endpoint webhook exposé publiquement en HTTPS.
4. Rate limits API respectés.

### Flow OAuth

```
Front         Back                                      Garmin
  │              │                                         │
  │──connect───▶│                                         │
  │             │──GET /oauth-service/oauth/request_token▶│
  │             │◀──oauth_token────────────────────────── │
  │◀── redirect ─┤                                        │
  │──────────────────auth user chez Garmin──────────────▶│
  │◀────────────────── redirect callback ───────────────  │
  │──callback──▶│                                         │
  │             │──POST access_token────────────────────▶│
  │             │◀──access + refresh + user_api_id────── │
  │             │  (stocke en garmin_credentials, chiffré)│
  │◀──ok──────  │                                         │
```

Endpoints backend :
- `GET /api/v1/garmin/connect/start` → renvoie l'URL de redirection.
- `GET /api/v1/garmin/connect/callback` → échange le code, enregistre les tokens.
- `POST /api/v1/garmin/webhooks/activities` → **webhook Garmin**, non authentifié par JWT mais **signé** (vérif HMAC), ip-allowlist en bonus.
- `DELETE /api/v1/garmin/connect` → révoque, supprime `garmin_credentials`.

### Ingestion par webhook

```
POST /webhooks/activities (Garmin)
  body: [{ userAccessToken, activityFileId, callbackURL, ... }]
   │
   ▼
GarminWebhookController  → répond 200 immédiatement, enqueue job
   │
   ▼
GarminIngestJob (async)
   │
   ├─► GarminApiClient.fetchActivity(activityFileId)   → JSON complet
   ├─► ActivityNormalizer.fromGarminApi(json)          → Activity
   ├─► ActivityDedupService (external_id = Garmin activityId, source=GARMIN_API)
   └─► saveAll + event
```

Le webhook Garmin **exige un ACK < 5 s** — on n'appelle jamais l'API Garmin dans le controller, on enqueue.

### Reprise et backfill

- À la connexion initiale : job de backfill (`GarminBackfillJob`) qui récupère les activités des 6 derniers mois par batch de 200 (rate limit Garmin ~1000 req/jour).
- Statut visible côté user : "Import de tes activités en cours (35 %)".
- En cas d'échec / token expiré → refresh transparent OU notification "Reconnecte ton compte Garmin".

### Cohabitation des sources

Un même utilisateur peut avoir importé un CSV **puis** connecté son compte. Règles :
- `source=GARMIN_API` **prime** sur `GARMIN_CSV` en cas de conflit (clé de dédup naturelle sur `started_at ± 60s + distance ± 100m`).
- Un job `ActivityReconciliationJob` (une fois à la connexion) fusionne les doublons anciens.

## Modèle Activity — champs & sources

| Champ | CSV | Garmin API |
|---|---|---|
| `activity_type` | mapping libellé FR | `activityType` enum |
| `started_at` | col Date + timezone user | `startTimeInSeconds` UTC |
| `duration_sec` | col Temps | `durationInSeconds` |
| `distance_m` | col Distance × 1000 | `distanceInMeters` |
| `avg_pace_sec_km` | col Allure moyenne | dérivé duration/distance |
| `avg_hr` | col FC moyenne | `averageHeartRateInBeatsPerMinute` |
| `elevation_gain_m` | col Élévation totale | `totalElevationGainInMeters` |
| `raw` | ligne CSV brute | JSON API complet |

## Tests

- `CsvParserTest` avec **une dizaine de fichiers de fixtures** : format FR, format EN, avec BOM, avec séparateur `;`, avec activités multi-sports, avec lignes malformées.
- `ActivityDedupServiceTest` : ré-import du même CSV → 0 doublon.
- `GarminApiClientTest` : mock du serveur Garmin (WireMock) — happy path + rate limit + token expiré.
