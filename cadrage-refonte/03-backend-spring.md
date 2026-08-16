# 03 — Backend Spring Boot

## Conventions

- **Java 21**, records pour les DTOs, `sealed` interfaces pour les payloads polymorphes (ex. `CoachAction`).
- **Convention REST** : versionnage dans l'URL `/api/v1/…`. Réponses JSON `camelCase`. Codes HTTP standard. Enveloppe d'erreur unique :

```json
{ "error": { "code": "QUOTA_EXCEEDED", "message": "…", "details": { … } } }
```

- **Idempotency-Key** obligatoire en header sur `POST /coach/chat` et `POST /prepas/{id}/weekly-update` (client génère un UUID) — évite les doubles décomptes de quota en cas de retry front.
- **Pagination** cursor-based (`?cursor=…&limit=50`), jamais offset.
- Un endpoint = un `@RestController` slim, la logique dans un `@Service`. Zéro logique métier dans les controllers.

## Endpoints REST — inventaire V1

### Auth

| Méthode | Path | Description |
|---|---|---|
| POST | `/api/v1/auth/register` | email + password + displayName, envoie mail de vérif |
| POST | `/api/v1/auth/login` | → `{ accessToken, refreshToken }` |
| POST | `/api/v1/auth/refresh` | échange refresh contre nouveau access |
| POST | `/api/v1/auth/logout` | invalide le refresh (Redis blocklist) |
| POST | `/api/v1/auth/verify-email` | via token |
| POST | `/api/v1/auth/forgot-password` | |
| POST | `/api/v1/auth/reset-password` | |
| GET | `/api/v1/auth/oauth/google/start` | (V1.1) |
| GET | `/api/v1/auth/oauth/google/callback` | |

### Users

| Méthode | Path | Description |
|---|---|---|
| GET | `/api/v1/me` | profil courant + plan + quotas restants |
| PATCH | `/api/v1/me` | displayName, timezone, locale |
| DELETE | `/api/v1/me` | RGPD, soft-delete + job de purge à 30 j |

### Prépas

| Méthode | Path | Description |
|---|---|---|
| GET | `/api/v1/prepas` | liste des prépas de l'user |
| POST | `/api/v1/prepas` | crée une prépa vide (avec objectifs bruts issus de l'onboarding) → renvoie un jobId de génération LLM |
| GET | `/api/v1/prepas/{id}` | prépa complète (objectifs + plan + agrégats) |
| PATCH | `/api/v1/prepas/{id}` | slug, isActive |
| DELETE | `/api/v1/prepas/{id}` | soft-delete |
| POST | `/api/v1/prepas/{id}/weekly-update` | déclenche le "point hebdo" LLM — voir §05 |
| POST | `/api/v1/prepas/{id}/activities/import-csv` | multipart, un fichier CSV Garmin |
| GET | `/api/v1/prepas/{id}/activities` | pagination |
| GET | `/api/v1/prepas/{id}/journal` | liste des entrées |
| PUT | `/api/v1/prepas/{id}/journal/{week}` | upsert d'une entrée hebdo |
| GET | `/api/v1/prepas/{id}/reports` | historique des rapports coach |

### Coach (chat)

| Méthode | Path | Description |
|---|---|---|
| POST | `/api/v1/coach/sessions` | crée une session (rattachée à une prépa) |
| GET | `/api/v1/coach/sessions` | liste des sessions récentes |
| GET | `/api/v1/coach/sessions/{id}/messages` | historique |
| POST | `/api/v1/coach/sessions/{id}/messages` | poste un message user → réponse assistant (streaming SSE si possible) |

### Admin

| Méthode | Path | Description |
|---|---|---|
| GET | `/api/v1/admin/users` | recherche |
| GET | `/api/v1/admin/llm-usage` | agrégats coût par jour/plan |
| POST | `/api/v1/admin/users/{id}/impersonate` | (log auditable) |

### Actuator (Spring)
`/actuator/health`, `/actuator/prometheus`, `/actuator/info` — sécurisés par IP allowlist.

## Sécurité — configuration Spring Security

```java
@Configuration
class SecurityConfig {
  @Bean SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http
      .csrf(csrf -> csrf.disable())          // stateless JWT, front séparé
      .cors(withDefaults())
      .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
      .authorizeHttpRequests(a -> a
        .requestMatchers("/api/v1/auth/**", "/actuator/health").permitAll()
        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated())
      .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
      .exceptionHandling(e -> e
        .authenticationEntryPoint(problemEntryPoint())
        .accessDeniedHandler(problemAccessDeniedHandler()))
      .build();
  }
}
```

- **JWT HS256** signé avec secret 256 bits monté par variable d'env (rotation possible via `kid` dans le header).
- Access token TTL **15 min**, refresh **30 j** stocké httpOnly côté front (V1 en `localStorage` + rotation stricte — accepté MVP, V2 durcir en cookie httpOnly SameSite=Strict).
- Le `JwtAuthFilter` extrait `userId` → attaché à `SecurityContext`, `@AuthenticationPrincipal AuthenticatedUser user` disponible dans les controllers.

## Concurrence & tâches asynchrones

Certaines opérations LLM prennent 10–30 s (génération de plan, weekly-update). Deux stratégies :

1. **Sync + timeout** pour les opérations rapides (chat, sanitize) — retour HTTP direct, SSE pour streamer les tokens de réponse.
2. **Async job** pour les opérations lourdes :
   - `POST /prepas` renvoie `202 { jobId }`.
   - Table `jobs (id, user_id, kind, status, result_json, error, created_at, updated_at)`.
   - Worker Spring `@Scheduled` polle les jobs `PENDING` OU on utilise directement `@Async` avec un thread pool dédié (V1 : `@Async` suffit — pas de bus interne prématuré).
   - Front polle `GET /jobs/{id}` (backoff exponentiel 500ms → 5s).

Virtual threads (`Thread.ofVirtual()`) pour l'exécution des jobs LLM — un job = un virtual thread, on n'a pas besoin de pool.

## Validation

- `jakarta.validation` sur tous les DTOs d'entrée. Contraintes précises (`@Email`, `@Size(min=8)`, custom `@ValidTimezone`).
- Validation métier dans les services (`Prepa.assertOwnedBy(user)` — méthode d'entité, jette `AccessDeniedException`).

## Gestion d'erreurs

`@ControllerAdvice` unique qui mappe les exceptions vers l'enveloppe `{ error }` :

| Exception | HTTP | code |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` |
| `AuthenticationException` | 401 | `UNAUTHENTICATED` |
| `AccessDeniedException` | 403 | `FORBIDDEN` |
| `EntityNotFoundException` | 404 | `NOT_FOUND` |
| `QuotaExceededException` | 429 | `QUOTA_EXCEEDED` |
| `LlmProviderException` | 502 | `LLM_UPSTREAM_ERROR` |
| `LlmBudgetRejectedException` | 429 | `TOKEN_BUDGET_EXCEEDED` |
| autre | 500 | `INTERNAL_ERROR` (masqué) |

Toutes les erreurs sont loguées avec `requestId` + `userId` (jamais le contenu).

## Tests

- **Unitaires** : services purs, `PromptBuilder`, `ContextCompactor` — Junit 5 + AssertJ.
- **Intégration** : Testcontainers Postgres+Redis, `@SpringBootTest`, MockMvc pour les endpoints.
- **Contract tests** LLM : `LlmClient` mocké par un stub qui rejoue des réponses recordées (fixtures JSON) — jamais d'appel réel en CI.
- Cible couverture : 70 % lignes global, 90 % sur `llm/` et `garmin/` (parsers critiques).

## Structure du projet

```
prepa-coach-api/
├── build.gradle.kts
├── src/main/java/com/prepacoach/api/
│   ├── PrepaCoachApplication.java
│   ├── auth/…
│   ├── user/…
│   ├── prepa/…
│   ├── activity/…
│   ├── journal/…
│   ├── coach/…
│   ├── llm/…
│   ├── garmin/…
│   ├── billing/…
│   ├── quota/…
│   ├── notification/…
│   └── infra/…
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   ├── application-prod.yml
│   ├── db/changelog/…                 # Liquibase
│   └── prompts/                       # Fichiers de prompts système versionnés
│       ├── coach-system-v1.md
│       ├── plan-generation-v1.md
│       └── weekly-update-v1.md
└── src/test/…
```
