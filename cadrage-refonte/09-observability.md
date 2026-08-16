# 09 — Observabilité

Trois piliers : **logs structurés**, **métriques** (dont métriques LLM propriétaires), **tracing** distribué.

## Logs

- **Format JSON** (Logback + `logstash-logback-encoder`), un événement par ligne.
- Champs standard : `ts`, `level`, `logger`, `msg`, `requestId`, `userId`, `service`, `env`.
- `requestId` généré par un `RequestIdFilter` en tête de chaîne, propagé via `MDC` et dans les headers sortants (`X-Request-Id`).
- Sortie stdout (12-factor), collecte par l'infra hébergeur (Loki / CloudWatch / Datadog).
- **Sanitizer** obligatoire (§08) — jamais de PII.

Niveaux :
- `ERROR` : exception non anticipée, échec LLM au-delà des retry, quota critique.
- `WARN` : retry, dépassement soft, comportement dégradé.
- `INFO` : opérations métier (login, import CSV, weekly-update terminé).
- `DEBUG` : désactivé en prod par défaut, activable par user via feature flag admin.

## Métriques (Micrometer → Prometheus)

Standard :
- `http_server_requests_seconds{method, uri, status}` (Spring auto).
- `jvm_*`, `process_*`, `hikari_*` (auto).

**Métriques LLM propriétaires — critiques** :

```
llm_calls_total{purpose, model, status}                     counter
llm_call_duration_seconds{purpose, model}                   histogram
llm_tokens_in_total{purpose, model, cached=true|false}      counter
llm_tokens_out_total{purpose, model}                        counter
llm_cost_micros_total{purpose, model, plan}                 counter
llm_cache_hit_ratio{purpose}                                gauge   ← SLO
llm_budget_rejected_total{purpose}                          counter
```

Métriques quotas :
```
quota_usage_ratio{plan, kind}                               histogram (0..1)
quota_exceeded_total{plan, kind}                            counter
```

Métriques import :
```
garmin_csv_import_seconds                                   histogram
garmin_csv_activities_total{result=imported|dup|skip}       counter
garmin_api_calls_total{endpoint, status}                    counter  (V2)
```

## Dashboards Grafana (à provisionner)

1. **LLM Cost** — coût cumulé jour/semaine/mois, par purpose, par plan. Alertes seuils.
2. **LLM Quality** — cache hit ratio (SLO > 60 %), taux d'erreur JSON schema validation, latence p50/p95/p99.
3. **User Funnel** — inscription → verif email → onboarding → import → 1er chat.
4. **API Health** — RED classique (Rate, Errors, Duration).
5. **Garmin Integration** — succès import CSV, en V2 : ingestion webhook, backlog.

## Alertes (Alertmanager)

Sévérité **critique** (page immédiate) :
- Taux d'erreur 5xx > 2 % sur 5 min.
- Latence p95 endpoints `/coach/*` > 15 s sur 5 min.
- Aucun appel LLM réussi sur 10 min (upstream down).
- Coût LLM cumulé journalier > budget * 1,5.

Sévérité **warning** (email/Slack) :
- Cache hit ratio LLM < 40 % pendant 1 h.
- Quota `WEEKLY_UPDATE` dépassé par > 5 % des users en 24 h → capacité mal calibrée.
- Erreurs Liquibase au boot.

## Tracing (V2)

- **OpenTelemetry Java Agent**, export OTLP vers Tempo/Jaeger.
- Span par requête HTTP, sous-spans : `db.query`, `redis.op`, `llm.call` (avec attributs `purpose`, `model`, `tokens_in`, `tokens_out`).
- Corrélation `traceId` ↔ `requestId` dans les logs.

## Health checks

- `/actuator/health` : compose Postgres + Redis + Anthropic (ping) + Garmin (ping V2).
- `/actuator/health/liveness` : basique (JVM up).
- `/actuator/health/readiness` : DB + Redis. Le check Anthropic **n'est pas readiness** — on ne veut pas retirer le pod du LB si Anthropic hoquette.

## Audit trail

Événements de sécurité loggés dans une table dédiée `audit_events` (append-only) :
- Login réussi / échoué.
- Changement mot de passe.
- Impersonation admin.
- Suppression compte.
- Connexion / déconnexion Garmin.

Rétention 24 mois. Accessible via `/api/v1/admin/audit`.

## Reporting

Job hebdomadaire → email récap admins :
- Nouveaux users, actifs, désabonnements.
- Coût LLM total, par plan, ratio coût/user.
- Top 10 users par coût (détection anomalies).
- Erreurs récurrentes (top 5).

## SLO

| Indicateur | Cible |
|---|---|
| Disponibilité API | 99,5 % / mois |
| Latence `/coach/chat` p95 | < 8 s |
| Latence autres endpoints p95 | < 500 ms |
| Cache hit ratio LLM | > 60 % (moyenne 7 j) |
| Coût LLM / user actif Free | < 0,15 €/mois |
